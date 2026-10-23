package ca.bc.gov.nrs.hbs.api.intake.xml;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Reads a scale-data file into a {@link Submission}: the replacement for the
 * legacy Castor unmarshal with {@code Submission.mapping} (schema V1/V2, the
 * "default" mapping) or {@code Submission_V3.mapping} (V3).
 *
 * <p>Like Castor with those mappings it matches elements by local name
 * (namespaces ignored), fills absent primitives with 0 / {@code '\0'} and
 * absent objects/Strings with null, and fails the whole file — format edit
 * FXA00 in B1012 — when a value cannot be converted.
 *
 * <p>The version comes from the {@code xsi:schemaLocation} value, as
 * {@code Validator.getXsdVersion} did. A file without one used the default
 * mapping in legacy; P505 only accepts the V3 namespace
 * ({@code ScaleDataXmlValidator}), so here such a file is read with the V3
 * mapping when its root is in the v3 namespace (legacy would have misread its
 * ISO dates and rejected every batch with FBA02).
 */
public class SubmissionUnmarshaller {

  public static final String V1 = "V1";
  public static final String V2 = "V2";
  public static final String V3 = "V3";
  public static final String DEFAULT = "default";

  private static final String NS_V3 = "gov.bc.ca/forests/hbs/v3";

  /** {@code Validator.getXsdVersion}. */
  public static String xsdVersion(String xml) {
    if (xml.contains("xsi:schemaLocation=\"gov.bc.ca/forests/hbs/v1")) {
      return V1;
    }
    if (xml.contains("xsi:schemaLocation=\"gov.bc.ca/forests/hbs/v2")) {
      return V2;
    }
    if (xml.contains("xsi:schemaLocation=\"gov.bc.ca/forests/hbs/v3")) {
      return V3;
    }
    return DEFAULT;
  }

  /** Unmarshals the file; any parse or conversion failure is FXA00. */
  public Submission unmarshal(byte[] xml) throws FormatEditException {
    try {
      Element root = parse(xml).getDocumentElement();
      String version = xsdVersion(new String(xml, StandardCharsets.UTF_8));
      if (DEFAULT.equals(version) && NS_V3.equals(root.getNamespaceURI())) {
        version = V3;
      }
      return new Reader(V3.equals(version)).submission(root, version);
    } catch (RuntimeException | javax.xml.parsers.ParserConfigurationException | org.xml.sax.SAXException
        | java.io.IOException ex) {
      throw new FormatEditException(FormatEdit.Transmission.VALIDATION);
    }
  }

  private static org.w3c.dom.Document parse(byte[] xml) throws javax.xml.parsers.ParserConfigurationException,
      org.xml.sax.SAXException, java.io.IOException {
    DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
    f.setNamespaceAware(true);
    f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    f.setExpandEntityReferences(false);
    f.setXIncludeAware(false);
    DocumentBuilder b = f.newDocumentBuilder();
    b.setErrorHandler(null);
    return b.parse(new ByteArrayInputStream(xml));
  }

  /** One unmarshal, with the mapping variant fixed. */
  private static final class Reader {
    private final boolean v3;

    Reader(boolean v3) {
      this.v3 = v3;
    }

    Submission submission(Element root, String version) {
      Submission s = new Submission();
      s.schemaVersion = version;
      Element tx = child(root, "transmission");
      if (tx != null) {
        Submission.XmlTransmission t = new Submission.XmlTransmission();
        t.batchCount = intOf(text(tx, "BatchCount"));
        t.type = charOf(text(tx, "Type"));
        if (t.type != 'P' && t.type != 'T') {
          throw new IllegalArgumentException("Type must be either P for production or T for test");
        }
        t.creator = text(tx, "Creator");
        if (v3) {
          t.software = software(child(tx, "Software"));
        }
        s.transmission = t;
      }
      docs(root, "WSB", "WSBDocument", Submission.BatchKind.WSB, s.weighSlipBillables, this::wsb);
      docs(root, "WSR", "WSRDocument", Submission.BatchKind.WSR, s.weighSlipRedTags, this::wsr);
      docs(root, "WSO", "WSODocument", Submission.BatchKind.WSO, s.weighSlipOthers, this::wso);
      docs(root, "PPS", "PPSDocument", Submission.BatchKind.PPS, s.pieceScales, this::pps);
      docs(root, "PSS", "PSSDocument", Submission.BatchKind.PSS, s.sampleScales, this::pss);
      docs(root, "POT", "POTDocument", Submission.BatchKind.POT, s.otherTallies, this::pot);
      docs(root, "SFP", "SFPDocument", Submission.BatchKind.SFP, s.specialForestProducts, this::sfp);
      for (Element e : children(root, "ARL")) {
        Submission.LedgerBatch b = new Submission.LedgerBatch("A");
        b.header = header(child(e, "BatchHeader"));
        children(e, "ARLEntry").forEach(x -> b.entries.add(arrival(x)));
        s.arrivalLedgers.add(b);
      }
      for (Element e : children(root, "DPL")) {
        Submission.LedgerBatch b = new Submission.LedgerBatch("D");
        b.header = header(child(e, "BatchHeader"));
        children(e, "DPLEntry").forEach(x -> b.entries.add(departure(x)));
        s.departureLedgers.add(b);
      }
      return s;
    }

    private void docs(Element root, String batchName, String docName, Submission.BatchKind kind,
        List<Submission.DocumentBatch> target, Function<Element, Document> reader) {
      for (Element e : children(root, batchName)) {
        Submission.DocumentBatch b = new Submission.DocumentBatch(kind);
        b.header = header(child(e, "BatchHeader"));
        children(e, docName).forEach(d -> b.documents.add(reader.apply(d)));
        target.add(b);
      }
    }

    private Submission.BatchHeader header(Element e) {
      if (e == null) {
        return null;
      }
      Submission.BatchHeader h = new Submission.BatchHeader();
      h.scaleSite = text(e, "ScaleSite");
      h.fromScaleDate = batchDate(text(e, "FromScaleDate"));
      h.toScaleDate = batchDate(text(e, "ToScaleDate"));
      h.submitterBatchId = text(e, "SubmitterBatchID");
      h.documentCount = intOf(text(e, "DocumentCount"));
      h.batchControlTotal = intOf(text(e, "BatchControlTotal"));
      return h;
    }

    /** {@code setFromScaleDateString/ISO}: unparseable → the error date (setter not called → null). */
    private java.util.Date batchDate(String text) {
      if (text == null) {
        return null;
      }
      java.util.Date d = LegacyDates.parseLenient(v3 ? LegacyDates.DATE_FORMAT_WITH_DASHES
          : LegacyDates.DATE_FORMAT_NO_TIME, text);
      return d == null ? LegacyDates.errorDate() : d;
    }

    private java.util.Date commonDate(String text) {
      return LegacyDates.parseLenient(v3 ? LegacyDates.DATE_FORMAT_WITH_DASHES : LegacyDates.DATE_FORMAT_NO_TIME,
          text);
    }

    private void base(Document d, Element e) {
      d.detailDocumentNumber = attr(e, "DDN");
      d.documentVersion = intOf(attr(e, "DV"));
      d.eventType = text(e, "EventType");
    }

    private Commons.WeighSlipCommon wsCommon(Element e) {
      if (e == null) {
        return null;
      }
      Commons.WeighSlipCommon c = new Commons.WeighSlipCommon();
      c.scaleSite = text(e, "ScaleSite");
      c.scaleDate = commonDate(text(e, "ScaleDate"));
      String time = text(e, "ScaleTime");
      if (time != null) {
        if (v3) {
          c.setScaleTimeIso(time);
        } else {
          c.scaleTimeValue = intOf(time);
        }
      }
      c.weighSlipNumber = intOf(text(e, "WeighSlipNumber"));
      c.primaryScalerLicence = text(e, "PrimaryScalerLicence");
      c.secondaryScalerLicence = text(e, "SecondaryScalerLicence");
      c.comment = text(e, "Comment");
      c.software = software(child(e, "Software"));
      c.digest = digest(child(e, "EC_Digest"));
      c.signature = signature(child(e, "EC_Signature"));
      return c;
    }

    private Commons.PieceScaleCommon psCommon(Element e) {
      if (e == null) {
        return null;
      }
      Commons.PieceScaleCommon c = new Commons.PieceScaleCommon();
      c.scaleSite = text(e, "ScaleSite");
      c.scaleDate = commonDate(text(e, "ScaleDate"));
      c.primaryScalerLicence = text(e, "PrimaryScalerLicence");
      c.primaryScalerReturnNumber = shortOf(text(e, "PrimaryScalerReturnNumber"));
      c.secondaryScalerLicence = text(e, "SecondaryScalerLicence");
      c.software = software(child(e, "Software"));
      c.digest = digest(child(e, "EC_Digest"));
      c.signature = signature(child(e, "EC_Signature"));
      c.comment = text(e, "Comment");
      return c;
    }

    private Parts.Software software(Element e) {
      if (e == null) {
        return null;
      }
      Parts.Software s = new Parts.Software();
      s.product = text(e, "SoftwareProduct");
      s.version = text(e, "SoftwareVersion");
      s.revision = text(e, "SoftwareRevision");
      return s;
    }

    private Parts.Digest digest(Element e) {
      if (e == null) {
        return null;
      }
      Parts.Digest d = new Parts.Digest();
      d.method = text(e, "Method");
      if (v3) {
        d.ecHtx64 = text(e, "EC_HTX64");
      } else {
        d.hashTotal = longOf(text(e, "EC_HT"));
      }
      return d;
    }

    private Parts.Signature signature(Element e) {
      if (e == null) {
        return null;
      }
      Parts.Signature s = new Parts.Signature();
      s.signingScalerLicence = text(e, "SigningScalerLicence");
      s.method = text(e, "Method");
      if (v3) {
        s.signingDateIso = text(e, "SigningDate");
        s.signingTimeIso = text(e, "SigningTime");
        s.ecEncrSig = text(e, "EC_ENCR_SIG");
        s.identifier = text(e, "Identifier");
      } else {
        String akeht = text(e, "EC_AKEHT");
        s.authenticationKeyEncryptedHashTotal = akeht == null ? null : Long.valueOf(akeht.trim());
      }
      return s;
    }

    private Parts.WeighSlipWeights weights(Element e) {
      if (e == null) {
        return null;
      }
      Parts.WeighSlipWeights w = new Parts.WeighSlipWeights();
      w.gross = intOf(text(e, "Gross"));
      w.tare = intOf(text(e, "Tare"));
      String sd = text(e, "SampleDeduction");
      w.sampleDeduction = sd == null ? null : Integer.valueOf(sd.trim());
      return w;
    }

    private Parts.WeighSlipSource wsSource(Element e) {
      if (e == null) {
        return null;
      }
      Parts.WeighSlipSource s = new Parts.WeighSlipSource();
      s.source = text(e, "Source");
      s.incomingLdsNumber = text(e, "IncomingLDSNumber");
      s.transportIdentifier = text(e, "TransportIdentifier");
      s.cutblockId = text(e, "CutblockID");
      return s;
    }

    private Parts.SplitLoad splitLoad(Element e) {
      if (e == null) {
        return null;
      }
      Parts.SplitLoad s = new Parts.SplitLoad();
      s.parcelId = byteOf(text(e, "ParcelID"));
      s.parcelCount = byteOf(text(e, "ParcelCount"));
      return s;
    }

    private Parts.CheckScale checkScale(Element e) {
      if (e == null) {
        return null;
      }
      Parts.CheckScale c = new Parts.CheckScale();
      c.originalScalerLicence = text(e, "OriginalScalerLicence");
      c.originalScalerReturnNumber = shortOf(text(e, "OriginalScalerReturnNumber"));
      String cro = text(e, "CheckReplacesOriginal");
      if (cro != null) {
        c.checkReplacesOriginal = yesNo(charOf(cro), "Check replaces original");
      }
      return c;
    }

    private Document wsb(Element e) {
      WeighSlipDocument.Billable d = new WeighSlipDocument.Billable();
      base(d, e);
      d.common = wsCommon(child(e, "WSCommon"));
      d.weights = weights(child(e, "WSWeights"));
      d.source = wsSource(child(e, "WSSource"));
      String fsf = text(e, "FieldScaleFlag");
      if (fsf != null) {
        d.fieldScaleFlag = yesNo(charOf(fsf), "Field scale flag");
      }
      d.fieldScaleDeckId = text(e, "FieldScaleDeckID");
      d.populationStrataYear = text(e, "PSY");
      d.outgoingLdsNumber = text(e, "OutgoingLDSNumber");
      d.destination = text(e, "Destination");
      d.sampleType = charOf(text(e, "SampleType"));
      return d;
    }

    private Document wsr(Element e) {
      WeighSlipDocument.RedTag d = new WeighSlipDocument.RedTag();
      base(d, e);
      d.common = wsCommon(child(e, "WSCommon"));
      d.weights = weights(child(e, "WSWeights"));
      d.source = wsSource(child(e, "WSSource"));
      String fsf = text(e, "FieldScaleFlag");
      if (fsf != null) {
        d.fieldScaleFlag = yesNo(charOf(fsf), "Field scale flag");
      }
      d.fieldScaleDeckId = text(e, "FieldScaleDeckID");
      d.populationStrataYear = text(e, "PSY");
      d.outgoingLdsNumber = text(e, "OutgoingLDSNumber");
      d.destination = text(e, "Destination");
      return d;
    }

    private Document wso(Element e) {
      WeighSlipDocument.Other d = new WeighSlipDocument.Other();
      base(d, e);
      d.common = wsCommon(child(e, "WSCommon"));
      d.populationStrataYear = text(e, "PSY");
      d.outgoingLdsNumber = text(e, "OutgoingLDSNumber");
      d.incomingLdsNumber = text(e, "IncomingLDSNumber");
      d.destination = text(e, "Destination");
      d.transportIdentifier = text(e, "TransportIdentifier");
      d.source = text(e, "Source");
      return d;
    }

    private void tallyBase(LogTallyDocument d, Element e) {
      base(d, e);
      d.common = psCommon(child(e, "PSCommon"));
      d.loadArrivalNumber = text(e, "LoadArrivalNumber");
      d.incomingLdsNumber = text(e, "IncomingLDSNumber");
      d.transportIdentifier = text(e, "TransportIdentifier");
      d.source = text(e, "Source");
    }

    private Document pps(Element e) {
      LogTallyDocument.PieceScale d = new LogTallyDocument.PieceScale();
      tallyBase(d, e);
      d.cutblockId = text(e, "CutblockID");
      String fsf = text(e, "FieldScaleFlag");
      if (fsf != null) {
        d.fieldScaleFlag = yesNo(charOf(fsf), "Field scale flag");
      }
      d.fieldScaleDeckId = characterOf(text(e, "FieldScaleDeckID"));
      d.weighSlipNumber = text(e, "WeighSlipNumber");
      d.originatingScaleSiteNumber = text(e, "OriginatingScaleSiteNumber");
      d.splitLoad = splitLoad(child(e, "SplitLoad"));
      Character beachcomb = characterOf(text(e, "Beachcomb"));
      if (beachcomb != null && beachcomb != 'Y' && beachcomb != 'N') {
        throw new IllegalArgumentException("Beachcomb must be either Y for yes, N for no, or null.");
      }
      d.beachcomb = beachcomb;
      d.checkScale = checkScale(child(e, "CheckScale"));
      d.pieceScaleMeasure = psMeasure(child(e, "PSMeasure"));
      return d;
    }

    private Document pss(Element e) {
      LogTallyDocument.SampleScale d = new LogTallyDocument.SampleScale();
      tallyBase(d, e);
      d.populationStrataYear = text(e, "PSY");
      d.weighSlipNumber = text(e, "WeighSlipNumber");
      d.sampleWeight = intOf(text(e, "SampleWeight"));
      d.originatingScaleSiteNumber = text(e, "OriginatingScaleSiteNumber");
      d.checkScale = checkScale(child(e, "CheckScale"));
      d.pieceScaleMeasure = psMeasure(child(e, "PSMeasure"));
      return d;
    }

    private Document pot(Element e) {
      LogTallyDocument.OtherTally d = new LogTallyDocument.OtherTally();
      tallyBase(d, e);
      d.splitLoad = splitLoad(child(e, "SplitLoad"));
      return d;
    }

    private Document sfp(Element e) {
      LogTallyDocument.SpecialForestProduct d = new LogTallyDocument.SpecialForestProduct();
      tallyBase(d, e);
      d.cutblockId = text(e, "CutblockID");
      String fsf = text(e, "FieldScaleFlag");
      if (fsf != null) {
        d.fieldScaleFlag = yesNo(charOf(fsf), "Field scale flag");
      }
      d.fieldScaleDeckId = characterOf(text(e, "FieldScaleDeckID"));
      d.checkScale = checkScale(child(e, "CheckScale"));
      d.splitLoad = splitLoad(child(e, "SplitLoad"));
      Element m = child(e, "SFPMeasure");
      if (m != null) {
        LogTallyDocument.SfpMeasure measure = new LogTallyDocument.SfpMeasure();
        measure.scaleMethod = charOf(text(m, "ScaleMethod"));
        measure.detailRecordCount = shortOf(text(m, "DetailRecordCount"));
        List<Element> stacks = children(m, "Stack");
        if (!stacks.isEmpty()) {
          measure.stacks = new ArrayList<>();
          for (Element s : stacks) {
            LogTallyDocument.SfpStack st = new LogTallyDocument.SfpStack();
            st.product = text(s, "PR");
            st.number = shortOf(text(s, "StackNumber"));
            st.species = text(s, "SP");
            st.lengthValue = shortOf(text(s, "LT"));
            st.widthValue = shortOf(text(s, "WD"));
            st.heightValue = shortOf(text(s, "HT"));
            st.conversionFactor = shortOf(text(s, "CnvFactor"));
            st.volumeValue = intOf(text(s, "SolidVol"));
            measure.stacks.add(st);
          }
        }
        List<Element> bundles = children(m, "Bundle");
        if (!bundles.isEmpty()) {
          measure.bundles = new ArrayList<>();
          for (Element b : bundles) {
            LogTallyDocument.SfpBundle bu = new LogTallyDocument.SfpBundle();
            bu.product = text(b, "PR");
            bu.number = shortOf(text(b, "BundleNumber"));
            bu.pieceCount = shortOf(text(b, "PieceCount"));
            bu.species = text(b, "SP");
            bu.grade = charOf(text(b, "GR"));
            measure.bundles.add(bu);
          }
        }
        Element lm = child(m, "SFPLogMeasure");
        if (lm != null) {
          LogTallyDocument.SfpLogMeasure logMeasure = new LogTallyDocument.SfpLogMeasure();
          String ppc = text(lm, "ProratedPieceCount");
          logMeasure.proratedPieceCount = ppc == null ? null : Long.valueOf(ppc.trim());
          List<Element> logs = children(lm, "Log");
          if (!logs.isEmpty()) {
            logMeasure.logs = new ArrayList<>();
            for (Element l : logs) {
              LogTallyDocument.SfpLog log = new LogTallyDocument.SfpLog();
              log.product = text(l, "PR");
              log.number = shortOf(text(l, "LogNumber"));
              log.species = text(l, "SP");
              log.lengthValue = shortOf(text(l, "LT"));
              log.top = shortOf(text(l, "TP"));
              log.butt = shortOf(text(l, "BT"));
              log.volumeValue = intOf(text(l, "NetVolume"));
              logMeasure.logs.add(log);
            }
          }
          measure.logMeasure = logMeasure;
        }
        d.measure = measure;
      }
      return d;
    }

    private LogTallyDocument.PieceScaleMeasure psMeasure(Element e) {
      if (e == null) {
        return null;
      }
      LogTallyDocument.PieceScaleMeasure m = new LogTallyDocument.PieceScaleMeasure();
      List<Element> logs = children(e, "Log");
      if (!logs.isEmpty()) {
        m.logs = new ArrayList<>();
        for (Element l : logs) {
          LogTallyDocument.Log log = new LogTallyDocument.Log();
          log.number = shortOf(text(l, "LogNumber"));
          log.timbermark = text(l, "TM");
          log.nmv = characterOf(text(l, "NM"));
          log.species = text(l, "SP");
          log.grade = charOf(text(l, "GR"));
          log.lengthValue = shortOf(text(l, "LT"));
          log.top = shortOf(text(l, "TP"));
          log.butt = shortOf(text(l, "BT"));
          log.volumeValue = intOf(text(l, "NetVolume"));
          log.defectCount = byteOf(text(l, "DefectCount"));
          List<Element> defects = children(l, "Defect");
          if (!defects.isEmpty()) {
            log.defects = new ArrayList<>();
            for (Element x : defects) {
              LogTallyDocument.Defect d = new LogTallyDocument.Defect();
              d.number = byteOf(text(x, "DefectNumber"));
              d.code = text(x, "DefectCode");
              d.volumeValue = intOf(text(x, "DefectVolume"));
              d.description = text(x, "DefectFmtDesc");
              log.defects.add(d);
            }
          }
          m.logs.add(log);
        }
      }
      m.logCount = shortOf(text(e, "LogCount"));
      m.scaleMethod = charOf(text(e, "ScaleMethod"));
      return m;
    }

    private LedgerEntry arrival(Element e) {
      LedgerEntry.ArrivalEntry a = new LedgerEntry.ArrivalEntry();
      a.eventType = text(e, "EventType");
      a.scaleSite = text(e, "ScaleSite");
      Element arr = child(e, "Arrival");
      if (arr != null) {
        a.arrival = new Parts.Arrival();
        a.arrival.arrivalDateString = text(arr, "ArrivalDate");
        a.arrival.loadArrivalNumber = text(arr, "LoadArrivalNumber");
        a.arrival.incomingLdsNumber = text(arr, "IncomingLDSNumber");
        a.arrival.transportIdentifier = text(arr, "TransportIdentifier");
      }
      a.source = text(e, "Source");
      Element sc = child(e, "Scale");
      if (sc != null) {
        a.scale = new Parts.Scale();
        a.scale.scaleDateString = text(sc, "ScaleDate");
        a.scale.primaryScalerLicence = text(sc, "PrimaryScalerLicence");
        a.scale.primaryScalerReturnNumber = shortOf(text(sc, "PrimaryScalerReturnNumber"));
        a.scale.splitLoad = splitLoad(child(sc, "SplitLoad"));
      }
      a.fieldScaleFlag = characterOf(text(e, "FieldScaleFlag"));
      a.fieldScaleDeckId = characterOf(text(e, "FieldScaleDeckID"));
      a.cutblockId = text(e, "CutblockID");
      a.weighSlipNumber = text(e, "WeighSlipNumber");
      a.originatingScaleSiteNumber = text(e, "OriginatingScaleSiteNumber");
      return a;
    }

    private LedgerEntry departure(Element e) {
      LedgerEntry.DepartureEntry d = new LedgerEntry.DepartureEntry();
      d.eventType = text(e, "EventType");
      d.scaleSite = text(e, "ScaleSite");
      d.departureDateString = text(e, "DepartureDate");
      d.outgoingLdsNumber = text(e, "OutgoingLDSNumber");
      d.destination = text(e, "Destination");
      d.departureNumber = text(e, "DepartureNumber");
      d.outgoingTransportIdentifier = text(e, "OutgoingTransportIdentifier");
      return d;
    }
  }

  // ── DOM helpers (local names, direct children only) ──────────────────────

  static Element child(Element parent, String name) {
    for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
      if (n instanceof Element e && name.equals(localName(e))) {
        return e;
      }
    }
    return null;
  }

  static List<Element> children(Element parent, String name) {
    List<Element> list = new ArrayList<>();
    for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
      if (n instanceof Element e && name.equals(localName(e))) {
        list.add(e);
      }
    }
    return list;
  }

  private static String localName(Element e) {
    return e.getLocalName() != null ? e.getLocalName() : e.getTagName();
  }

  static String text(Element parent, String name) {
    Element e = child(parent, name);
    return e == null ? null : e.getTextContent();
  }

  static String attr(Element e, String name) {
    return e.hasAttribute(name) ? e.getAttribute(name) : null;
  }

  static int intOf(String s) {
    return s == null ? 0 : Integer.parseInt(s.trim());
  }

  static short shortOf(String s) {
    return s == null ? 0 : Short.parseShort(s.trim());
  }

  static byte byteOf(String s) {
    return s == null ? 0 : Byte.parseByte(s.trim());
  }

  static long longOf(String s) {
    return s == null ? 0 : Long.parseLong(s.trim());
  }

  static char charOf(String s) {
    return s == null || s.isEmpty() ? '\0' : s.charAt(0);
  }

  static Character characterOf(String s) {
    return s == null || s.isEmpty() ? null : s.charAt(0);
  }

  static char yesNo(char c, String what) {
    if (c != 'Y' && c != 'N') {
      throw new IllegalArgumentException(what + " must be either Y for yes or N for no");
    }
    return c;
  }
}
