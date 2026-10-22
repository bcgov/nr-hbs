package ca.bc.gov.nrs.hbs.api.detailreturns.command;

import ca.bc.gov.nrs.hbs.api.detailreturns.DetailAuthority.Button;
import ca.bc.gov.nrs.hbs.api.detailreturns.DetailPayload;
import ca.bc.gov.nrs.hbs.api.detailreturns.EventChange;
import ca.bc.gov.nrs.hbs.api.detailreturns.FieldAvailability;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DetailDocument;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.DocType;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.LogLine;
import ca.bc.gov.nrs.hbs.api.detailreturns.model.Row;
import ca.bc.gov.nrs.hbs.api.exception.EntityNotFoundException;
import ca.bc.gov.nrs.hbs.api.security.HbsAuthorities;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * What the detail-return entry pages and action buttons need beyond the
 * registry queries: one version as an editable document (every column, its
 * log lines and defects, the UPDATE_TIMESTAMP to send back) plus the buttons
 * the caller may press on it ({@code FunctionsComparator} + Buttons.jsp), and
 * the legacy field-availability matrices.
 *
 * <pre>
 *   GET /api/v1/hbs/detail-returns/{ddn}/versions/{version}   → document + actions
 *   GET /api/v1/hbs/detail-returns/fields                     → field availability by type/event
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/hbs/detail-returns")
@Tag(name = "Detail scale returns", description = "Data entry and workflow actions (legacy P550–P760)")
public class DetailEntryController {

  private final DetailCommandSupport s;

  public DetailEntryController(DetailCommandSupport s) {
    this.s = s;
  }

  @GetMapping("/{ddn}/versions/{version}")
  @PreAuthorize(HbsAuthorities.SCALE_RETURNS_VIEW)
  @Operation(summary = "One detail return version for editing, with the actions the caller may take")
  public Map<String, Object> version(@PathVariable String ddn, @PathVariable long version) {
    DetailDocument doc = s.store().find(ddn.toUpperCase(), version)
        .orElseThrow(() -> new EntityNotFoundException(DetailDocument.class, "ddn", ddn, "version", "" + version));
    s.fence(doc);
    var caller = s.caller();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("ddn", doc.ddn());
    out.put("version", doc.version().number());
    out.put("docType", doc.type().code());
    out.put("documentState", doc.state().value());
    out.put("versionState", doc.version().state().value());
    out.put("active", doc.version().state().isActive());
    out.put("updateTimestamp", DetailPayload.json(doc.version().row()).get("updateTimestamp"));
    out.put("fields", DetailPayload.json(doc.version().row()));
    List<Map<String, Object>> logs = new ArrayList<>();
    for (LogLine line : doc.version().logs()) {
      Map<String, Object> l = new LinkedHashMap<>();
      l.put("logdtlId", line.id());
      l.put("fields", DetailPayload.json(line.row()));
      List<Map<String, Object>> defects = new ArrayList<>();
      for (Row d : line.defects()) {
        defects.add(Map.of("logDefectId", d.lng("log_defect_id"), "fields", DetailPayload.json(d)));
      }
      l.put("defects", defects);
      logs.add(l);
    }
    out.put("logs", logs);
    TreeSet<String> actions = new TreeSet<>();
    for (Button b : s.authority().shown(doc, caller)) {
      actions.addAll(commandsOf(b, doc));
    }
    out.put("actions", actions);
    out.put("editable", s.authority().isEditable(doc, caller));
    out.put("eventTypes", new TreeSet<>(EventChange.eventTypes(doc.type())));
    return out;
  }

  @GetMapping("/fields")
  @PreAuthorize(HbsAuthorities.SCALE_RETURNS_VIEW)
  @Operation(summary = "Legacy field availability by document type and scale event type")
  public Map<String, Object> fields(@RequestParam(required = false) String docType) {
    Map<String, Object> out = new LinkedHashMap<>();
    FieldAvailability.all().forEach((k, v) -> out.put(k, new TreeSet<>(v)));
    if (docType != null) {
      out.put("columns", DetailPayload.versionColumns(DocType.of(docType)).stream()
          .filter(DetailPayload::editable).map(DetailPayload::camel).toList());
    }
    return out;
  }

  /** Button → command ids (Buttons.jsp labels). */
  static List<String> commandsOf(Button b, DetailDocument doc) {
    return switch (b) {
      case APPROVE_CHANGE -> List.of("detailReturns.approve", "detailReturns.reject");
      case CANCEL -> List.of("detailReturns.cancel");
      case CANCEL_REPLACE -> List.of("detailReturns.cancel", "detailReturns.cancelReplaceChanges");
      case CLEAR_DIGITAL_SIGNATURE_FAILURE -> List.of("detailReturns.clearSignatureFailure");
      case DISCARD -> List.of("detailReturns.discard");
      case HOLD -> List.of("detailReturns.hold");
      case REEDIT -> List.of("detailReturns.reEdit");
      case RELEASE -> EventChange.eventTypes(doc.type()).isEmpty() ? List.of("detailReturns.release")
          : List.of("detailReturns.release", "detailReturns.changeEvent");
      case REPLACE -> List.of("detailReturns.replaceChanges", "detailReturns.replaceNoChanges");
      case SAVE -> List.of("detailReturns.save");
    };
  }
}
