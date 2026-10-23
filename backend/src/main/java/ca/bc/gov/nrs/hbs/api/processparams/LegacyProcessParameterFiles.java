package ca.bc.gov.nrs.hbs.api.processparams;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Rebuilds, from the settings ConfigMap ({@link AppSettings}), the three flat files legacy P038
 * wrote into {@code PROCESS_PARAMETER_FILE_PATH} (HBSConfiguration.properties;
 * batch tokens {@code cfg_p_01/02_parameter_file_path}, the ProcessParam share)
 * — byte for byte as
 * {@code P038Action} wrote them, because the legacy batch
 * ({@code HBSProcessingParamHelper.canProcess} for B2031/B2041,
 * {@code loadPrinterValues} for B2075) still parses them:
 *
 * <table>
 *   <tr><th>File (deploy token)</th><th>Content</th></tr>
 *   <tr><td>{@code HBSProcessingParameter.csv} (cfg_p_process_parameter_file)</td>
 *       <td>one line per process, {@code <process>,<startLoc>,<timeLimit>} + "\n"
 *       ({@code P038Action.saveProcessParam}, NEW_LINE = "\n")</td></tr>
 *   <tr><td>{@code Printer_names.txt} (cfg_p_printer_parameter_file)</td>
 *       <td>{@code COPY_TO_PRINTER=<copy>} "\n" {@code SEND_TO_PRINTER=<send>}, no final newline
 *       ({@code savePrinterNames}; '=' because the name doesn't end in "csv")</td></tr>
 *   <tr><td>{@code HBS_ALERT_FILE.txt} (cfg_p_alert_text_file)</td>
 *       <td>the alert text as typed, no newline added; empty after Delete
 *       ({@code saveAlertText} / {@code deleteAlertText})</td></tr>
 * </table>
 *
 * <p>Missing or invalid values: the process file needs all four start
 * location / time limit values as whole numbers ({@link #problems}); otherwise the bridge is refused rather
 * than writing defaults over what the batch uses now (legacy "Set to
 * Defaults" was 0 / 9999999999, which is entered in the ConfigMap like any
 * other value). A missing alert is the empty file legacy's Delete left.
 * Printer names have no legacy default, so the printer file is left out until
 * both names are set (writing blanks would stop B2075 printing).
 *
 * <p>Encoding: windows-1252, the default charset of the batch host's
 * {@code FileReader} (the only reader of these files once the legacy web UI
 * is gone); P038 accepted only ASCII digits and {@code \\server\printer} names
 * there, so the process and printer files are plain ASCII either way.
 */
public final class LegacyProcessParameterFiles {

  public static final String PROCESS_PARAMETER_FILE = "HBSProcessingParameter.csv";
  public static final String PRINTER_FILE = "Printer_names.txt";
  public static final String ALERT_TEXT_FILE = "HBS_ALERT_FILE.txt";
  public static final String ENCODING = "windows-1252";

  /** P038Action.NEW_LINE. */
  static final String NEW_LINE = "\n";

  private LegacyProcessParameterFiles() {
  }

  public record LegacyFile(String name, String content, String encoding) {}

  /** P038Action's printer-name check ({@code \\server\printer}). */
  static final Pattern PRINTER = Pattern.compile("\\\\{2}?[^\\\\]+\\\\{1}?[^\\\\]+");
  private static final Pattern WHOLE_NUMBER = Pattern.compile("\\d{1,10}");

  /**
   * What stops the files being built, as P038 would have refused it: each of
   * the four process values missing or not a whole number, and a printer name
   * set but not {@code \\server\printer}. Empty when the files can be built.
   */
  public static List<String> problems(Map<String, String> settings) {
    List<String> out = new ArrayList<>();
    for (String process : AppSettings.PROCESSES) {
      for (String suffix : List.of(AppSettings.START_LOC, AppSettings.TIME_LIMIT)) {
        String v = settings.get(process + suffix);
        if (v == null || v.isBlank()) {
          out.add(process + suffix + " is not set");
        } else if (!WHOLE_NUMBER.matcher(v.trim()).matches()) {
          out.add(process + suffix + " must be a whole number");
        }
      }
    }
    for (String key : List.of(AppSettings.SEND_TO, AppSettings.COPY_TO)) {
      String v = settings.get(key);
      if (v != null && !v.isBlank() && !PRINTER.matcher(v.trim()).matches()) {
        out.add(key + " must be \\\\server\\printer");
      }
    }
    return out;
  }

  public static List<LegacyFile> build(Map<String, String> settings) {
    List<String> problems = problems(settings);
    if (!problems.isEmpty()) {
      throw new IllegalStateException(String.join("; ", problems));
    }
    List<LegacyFile> files = new ArrayList<>();
    files.add(new LegacyFile(PROCESS_PARAMETER_FILE, processParameterCsv(settings), ENCODING));
    String printers = printerFile(settings);
    if (printers != null) {
      files.add(new LegacyFile(PRINTER_FILE, printers, ENCODING));
    }
    files.add(new LegacyFile(ALERT_TEXT_FILE, alertTextFile(settings), ENCODING));
    return files;
  }

  static String processParameterCsv(Map<String, String> settings) {
    StringBuilder b = new StringBuilder();
    for (String process : AppSettings.PROCESSES) {
      b.append(process).append(',')
          .append(new BigInteger(settings.get(process + AppSettings.START_LOC).trim()))
          .append(',')
          .append(new BigInteger(settings.get(process + AppSettings.TIME_LIMIT).trim()))
          .append(NEW_LINE);
    }
    return b.toString();
  }

  static String printerFile(Map<String, String> settings) {
    String copyTo = settings.get(AppSettings.COPY_TO);
    String sendTo = settings.get(AppSettings.SEND_TO);
    if (copyTo == null || copyTo.isBlank() || sendTo == null || sendTo.isBlank()) {
      return null;
    }
    return "COPY_TO_PRINTER=" + copyTo.trim() + NEW_LINE + "SEND_TO_PRINTER=" + sendTo.trim();
  }

  static String alertTextFile(Map<String, String> settings) {
    String alert = settings.get(AppSettings.ALERT_TEXT);
    return alert == null ? "" : alert;
  }
}
