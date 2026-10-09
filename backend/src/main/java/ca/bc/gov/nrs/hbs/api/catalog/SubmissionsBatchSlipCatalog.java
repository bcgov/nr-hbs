package ca.bc.gov.nrs.hbs.api.catalog;

import ca.bc.gov.nrs.hbs.api.query.Capability;
import ca.bc.gov.nrs.hbs.api.query.CommandDefinition;
import ca.bc.gov.nrs.hbs.api.query.QueryCatalog;
import ca.bc.gov.nrs.hbs.api.query.QueryDefinition;
import ca.bc.gov.nrs.hbs.api.query.QueryFilter;
import ca.bc.gov.nrs.hbs.api.query.QueryFilter.Type;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Paper batch tracking — batch slips P280 (Generate), P281/P282 (Search /
 * List) and P283 (Update), table {@code HBS_PAPER_BATCH}.
 *
 * <p>Legacy: {@code dac.srt.detail.P280Action} / {@code P281Action},
 * {@code PaperBatchQuery} (selectByPK + {@code hbs_create_hbs_paper_batch} /
 * {@code hbs_store_hbs_paper_batch}), list search proc {@code HBS3R281}, and
 * "Documents Received" = {@code SummaryScaleReturnQuery.selectCountByVolumeEstBatchId}
 * (count of HBS_SCALE_RETURN rows whose VOLEST_BATCH_ID is the slip).
 *
 * <p>The links to these screens were removed from P002 in 2010 (ticket 8835);
 * they are carried over (not in the nav) because the data and procs remain.
 */
@Component
public class SubmissionsBatchSlipCatalog implements QueryCatalog {

  static final String SLIP_SQL = """
      SELECT pb.batch_id,
             pb.paper_batch_status_code AS status_code,
             sc.description AS status_description,
             pb.scale_site_id_nmbr AS scale_site_no,
             pb.dtl_doc_batch_id,
             pb.hbs_return_type_code AS return_type,
             pb.from_scale_date AS scaled_from,
             pb.to_scale_date AS scaled_to,
             pb.document_count,
             pb.hdq_sent_date,
             pb.sender_userid,
             pb.paper_batch_comment,
             TO_CHAR(pb.org_unit_no) AS org_unit_no,
             ou.org_unit_name AS region_name,
             pb.entry_userid,
             pb.entry_timestamp,
             pb.update_userid,
             pb.update_timestamp,
             (SELECT NULLIF(COUNT(*), 0) FROM hbs_scale_return sr
               WHERE sr.volest_batch_id = pb.batch_id) AS documents_received
        FROM hbs_paper_batch pb
        LEFT JOIN paper_batch_status_code sc ON sc.paper_batch_status_code = pb.paper_batch_status_code
        LEFT JOIN org_unit ou ON ou.org_unit_no = pb.org_unit_no
       WHERE 1=1""";

  @Override
  public List<QueryDefinition> queries() {
    return List.of(
        // P281 → P282 (HBS3R281: batch id OR scale site + creation dates).
        QueryDefinition.builder("submissions.batchSlips")
            .legacy("P281/P282")
            .capability(Capability.MINISTRY)
            .sql(SLIP_SQL)
            .filter(QueryFilter.number("batchId", "AND pb.batch_id = :batchId"))
            .filter(QueryFilter.upper("scaleSiteNo", "AND pb.scale_site_id_nmbr = :scaleSiteNo"))
            .filter(QueryFilter.date("createdFrom", "AND pb.entry_timestamp >= :createdFrom"))
            .filter(QueryFilter.dateTo("createdTo", "AND pb.entry_timestamp < :createdTo"))
            .sort("batchId", "pb.batch_id")
            .sort("entryTimestamp", "pb.entry_timestamp")
            .sort("statusDescription", "sc.description")
            .sort("scaleSiteNo", "pb.scale_site_id_nmbr")
            .sort("scaledFrom", "pb.from_scale_date")
            .sort("scaledTo", "pb.to_scale_date")
            .orderBy("pb.batch_id DESC")
            .build(),

        // P283 view / edit pre-fill (PaperBatchQuery.selectByPK).
        QueryDefinition.builder("submissions.batchSlip")
            .legacy("P283")
            .capability(Capability.MINISTRY)
            .sql(SLIP_SQL)
            .filter(QueryFilter.required("batchId", "AND pb.batch_id = :batchId", Type.NUMBER))
            .maxRows(1)
            .build(),

        // P280 "Region" select (regions only).
        QueryDefinition.builder("codes.submissions.regions")
            .legacy("P280")
            .capability(Capability.ANY_USER)
            .sql("SELECT TO_CHAR(org_unit_no) AS code, org_unit_code || ' - ' || org_unit_name AS description"
                + " FROM org_unit WHERE org_level_code = 'R'"
                + " AND SYSDATE BETWEEN effective_date AND expiry_date")
            .orderBy("org_unit_name")
            .maxRows(100)
            .build(),

        CodesCatalog.codeTable("codes.submissions.paperBatchStatuses", "PAPER_BATCH_STATUS_CODE")
    );
  }

  @Override
  public List<CommandDefinition> commands() {
    return List.of(
        // P280 Save → createAndStorePaperBatch, status PND.
        CommandDefinition.builder("batchSlip.create")
            .legacy("P280")
            .capability(Capability.BATCH_SLIP_EDIT)
            .procedure("THE.HBS_CREATE_HBS_PAPER_BATCH")
            .sequence("HBS_PAPER_BATCH_SEQ")              // i_batch_id
            .constant("PND")                               // i_paper_batch_status_code
            .requiredBody("scaleSiteNo", Type.UPPER)       // i_scale_site_id_nmbr
            .constant("")                                  // i_dtl_doc_batch_id (null; set by B1012 matching)
            .constant("")                                  // i_hbs_return_type_code (null)
            .requiredBody("scaledFrom", Type.DATE)         // i_from_scale_date
            .requiredBody("scaledTo", Type.DATE)           // i_to_scale_date
            .requiredBody("documentCount", Type.NUMBER)    // i_document_count
            .requiredBody("hdqSentDate", Type.DATE)        // i_hdq_sent_date
            .requiredBody("senderUserid", Type.UPPER)      // i_sender_userid (DOMAIN\USERID)
            .text("paperBatchComment")                     // i_paper_batch_comment
            .requiredBody("orgUnitNo", Type.NUMBER)        // i_org_unit_no (region)
            .auditUser()                                   // i_entry_userid
            .now()                                         // i_entry_timestamp
            .constant("")                                  // i_update_userid (null, as legacy)
            .constant("")                                  // i_update_timestamp (null, as legacy)
            .build(),
        // P283 Save → updatePaperBatch (status unchanged).
        store("batchSlip.update", null),
        // P283 Cancel → status CLR.
        store("batchSlip.cancel", "CLR"),
        // P283 Confirm → status MAT (legacy also required docReceived == documentCount; see docs).
        store("batchSlip.confirm", "MAT")
    );
  }

  /** HBS_STORE_HBS_PAPER_BATCH overwrites every column, so every value is re-sent. */
  private static CommandDefinition store(String id, String status) {
    CommandDefinition.Builder b = CommandDefinition.builder(id)
        .legacy("P283")
        .capability(Capability.BATCH_SLIP_EDIT)
        .procedure("THE.HBS_STORE_HBS_PAPER_BATCH")
            .existingRow("SELECT entry_userid, entry_timestamp FROM hbs_paper_batch WHERE batch_id = :batchId")
        .requiredBody("batchId", Type.NUMBER);             // i_batch_id
    if (status == null) {
      b.requiredBody("statusCode", Type.UPPER);            // i_paper_batch_status_code
    } else {
      b.constant(status);
    }
    return b
        .requiredBody("scaleSiteNo", Type.UPPER)           // i_scale_site_id_nmbr
        .number("dtlDocBatchId")                           // i_dtl_doc_batch_id
        .upper("returnType")                               // i_hbs_return_type_code
        .requiredBody("scaledFrom", Type.DATE)             // i_from_scale_date
        .requiredBody("scaledTo", Type.DATE)               // i_to_scale_date
        .requiredBody("documentCount", Type.NUMBER)        // i_document_count
        .requiredBody("hdqSentDate", Type.DATE)            // i_hdq_sent_date
        .requiredBody("senderUserid", Type.UPPER)          // i_sender_userid
        .text("paperBatchComment")                         // i_paper_batch_comment
        .requiredBody("orgUnitNo", Type.NUMBER)            // i_org_unit_no
        .existing("entryUserid")          // i_entry_userid (unchanged)
        .existing("entryTimestamp")         // i_entry_timestamp (unchanged; day precision)
        .auditUser()                                       // i_update_userid
        .now()                                             // i_update_timestamp
        .build();
  }
}
