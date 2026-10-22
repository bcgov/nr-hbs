package ca.bc.gov.nrs.hbs.api.summaryreturns;

import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.BILL_ADMIN;
import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.CLI_CRUISE_ADMIN;
import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.INV_CORR_APP;
import static ca.bc.gov.nrs.hbs.api.security.HbsRoles.SUMM_DATA_CORR;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.ScaleReturnState.AWAITING_APPROVAL;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.ScaleReturnState.CANCELLED_AND_INVOICED;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.ScaleReturnState.ERROR;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.ScaleReturnState.FAILED;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.ScaleReturnState.HELD;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.ScaleReturnState.INCOMPLETE;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.ScaleReturnState.INVALID_STATE;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.ScaleReturnState.INVOICED;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.ScaleReturnState.NOT_TO_BE_BILLED;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.ScaleReturnState.ORIGINAL_IN_PROGRESS;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.ScaleReturnState.READY;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.ScaleReturnState.REPLACE_NO_CHANGES_IN_PROGRESS;
import static ca.bc.gov.nrs.hbs.api.summaryreturns.ScaleReturnState.REPLACE_WITH_CHANGES_IN_PROGRESS;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Which workflow buttons the legacy view page offered the caller for a return: port of
 * {@code $V/dac/summary/SummaryReturnFieldsTag.isFieldAvailable}, {@code ButtonsForCBATag} and the
 * role blocks of {@code P052.jsp / P102.jsp / P152.jsp} (plus the Release / Discard buttons of the
 * P053/P103/P153 edit page for users who may edit the return). The commands refuse an action that is
 * not in this set — the legacy app enforced these rules only by hiding the buttons.
 */
@Component
public class SummaryButtons {

  public static final String HOLD = "hold";
  public static final String RELEASE = "release";
  public static final String DISCARD = "discard";
  public static final String CANCEL = "cancel";
  public static final String CANCEL_REPLACE_WITH_CHANGES = "cancelReplaceWithChanges";
  public static final String CANCEL_REPLACE_WITHOUT_CHANGES = "cancelReplaceWithoutChanges";
  public static final String REPLACE_WITH_CHANGES = "replaceWithChanges";
  public static final String REPLACE_WITHOUT_CHANGES = "replaceWithoutChanges";
  public static final String REQUEST_RATE_CORRECTION = "requestRateCorrection";
  public static final String APPROVE_RATE_CORRECTION = "approveRateCorrection";
  public static final String REJECT_RATE_CORRECTION = "rejectRateCorrection";
  public static final String UPDATE = "update";

  private final SummaryReturnAccess access;

  public SummaryButtons(SummaryReturnAccess access) {
    this.access = access;
  }

  /** The actions available to the caller on this version. */
  public Set<String> available(SummaryCaller caller, SummaryReturn ret) {
    Set<String> out = new LinkedHashSet<>();
    String doc = ret.docState();
    String ver = ret.versionState();
    if (!ret.active() || INVALID_STATE.equals(ver)) {
      return out; // <logic:equal property="activeVersion" value="true">, INVALID_STATE
    }
    boolean generated = ret.generated();
    boolean piece = ret.type() == SummaryReturnType.PIECE;
    boolean waste = SummaryReturnWorkflows.isWasteBatchReturn(ret);
    boolean sample = ret.type() == SummaryReturnType.SAMPLE;
    String mark = ret.version().str("timber_mark");

    // P053/P103/P153 edit page: Save, Release, Discard for users who may edit (never generated).
    if (!generated && access.mayEdit(caller, ret)) {
      out.add(UPDATE);
      out.add(RELEASE);
      out.add(DISCARD);
    }

    // CLI_CRUISE_ADMIN block, filtered by ButtonsForCBATag (area based, mark held by the caller's client).
    if (caller.has(CLI_CRUISE_ADMIN) && ret.areaBased()
        && Objects.equals(access.markHolder(mark).orElse(null), caller.clientNumber())) {
      if (!generated && ORIGINAL_IN_PROGRESS.equals(doc)) out.add(HOLD);
      if (discardButton(caller, ret)) out.add(DISCARD);
    }

    // HBS_SUMM_DATA_CORR block.
    if (caller.has(SUMM_DATA_CORR)) {
      if (!generated) {
        if (INVOICED.equals(ver)) {
          out.add(CANCEL);
          if (!waste) {
            out.add(CANCEL_REPLACE_WITH_CHANGES);
            if (!sample) out.add(CANCEL_REPLACE_WITHOUT_CHANGES); // commented out on P152
          }
        }
        if (CANCELLED_AND_INVOICED.equals(ver) && !waste) {
          out.add(REPLACE_WITH_CHANGES);
          if (!sample) out.add(REPLACE_WITHOUT_CHANGES);
        }
        if (holdButton(caller, ret)) out.add(HOLD);
      }
      if (discardButton(caller, ret)) out.add(DISCARD);
      if (generated ? HELD.equals(ver) : (HELD.equals(ver) || (ORIGINAL_IN_PROGRESS.equals(doc) && INCOMPLETE.equals(ver)))) {
        out.add(RELEASE);
      }
    }

    // Generated summaries: rate corrections.
    if (generated) {
      if (caller.has(BILL_ADMIN) && INVOICED.equals(ver) && access.hasDistrictAuthorization(caller, mark)) {
        out.add(REQUEST_RATE_CORRECTION);
      }
      if (caller.has(INV_CORR_APP) && AWAITING_APPROVAL.equals(ver) && access.hasDistrictAuthorization(caller, mark)) {
        out.add(APPROVE_RATE_CORRECTION);
        out.add(REJECT_RATE_CORRECTION);
      }
    }

    // Volume estimates for users without SUMM_DATA_CORR / CLI_CRUISE_ADMIN.
    if (piece && ret.volumeEstimate() && !generated && !caller.hasAny(SUMM_DATA_CORR, CLI_CRUISE_ADMIN)) {
      boolean billAdminInDistrict = caller.has(BILL_ADMIN) && access.hasDistrictAuthorization(caller, mark);
      if (billAdminInDistrict && INVOICED.equals(ver)) {
        out.add(CANCEL);
        if (!waste) {
          out.add(CANCEL_REPLACE_WITH_CHANGES);
          out.add(CANCEL_REPLACE_WITHOUT_CHANGES);
        }
      }
      if (billAdminInDistrict && CANCELLED_AND_INVOICED.equals(ver) && !waste) {
        out.add(REPLACE_WITH_CHANGES);
        out.add(REPLACE_WITHOUT_CHANGES);
      }
      boolean custodian = (ORIGINAL_IN_PROGRESS.equals(doc)
          && (access.hasScaleSiteAuthorization(caller, ret.version().str("scale_site_id_nmbr"))
              || access.hasScalerAuthorization(caller, ret.version().str("license_number"))))
          || (REPLACE_WITH_CHANGES_IN_PROGRESS.equals(doc) && billAdminInDistrict);
      if ((custodian && (READY.equals(ver) || ERROR.equals(ver))) || NOT_TO_BE_BILLED.equals(ver)) {
        out.add(HOLD);
      }
      if (custodian && (READY.equals(ver) || ERROR.equals(ver) || NOT_TO_BE_BILLED.equals(ver))) {
        out.add(DISCARD);
      }
    }
    return out;
  }

  /** HOLD_BUTTON (not a volume estimate); CLI_CRUISE_ADMIN sees it in OIP only. */
  private boolean holdButton(SummaryCaller caller, SummaryReturn ret) {
    String doc = ret.docState();
    String ver = ret.versionState();
    if (caller.has(CLI_CRUISE_ADMIN)) return ORIGINAL_IN_PROGRESS.equals(doc);
    return ((ORIGINAL_IN_PROGRESS.equals(doc) || REPLACE_WITH_CHANGES_IN_PROGRESS.equals(doc))
        && (READY.equals(ver) || ERROR.equals(ver)))
        || (NOT_TO_BE_BILLED.equals(ver) && ret.type() == SummaryReturnType.PIECE);
  }

  /** DISCARD_BUTTON (not a volume estimate). */
  private boolean discardButton(SummaryCaller caller, SummaryReturn ret) {
    String doc = ret.docState();
    String ver = ret.versionState();
    if (ret.generated()) {
      return ERROR.equals(ver) && (ORIGINAL_IN_PROGRESS.equals(doc) || REPLACE_NO_CHANGES_IN_PROGRESS.equals(doc)
          || REPLACE_WITH_CHANGES_IN_PROGRESS.equals(doc));
    }
    if (caller.has(CLI_CRUISE_ADMIN)) return ORIGINAL_IN_PROGRESS.equals(doc);
    return ERROR.equals(ver) || READY.equals(ver) || (FAILED.equals(ver) && ORIGINAL_IN_PROGRESS.equals(doc))
        || (NOT_TO_BE_BILLED.equals(ver) && ret.type() == SummaryReturnType.PIECE);
  }
}
