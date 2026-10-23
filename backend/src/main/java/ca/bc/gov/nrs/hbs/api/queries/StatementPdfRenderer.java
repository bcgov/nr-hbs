package ca.bc.gov.nrs.hbs.api.queries;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopConfParser;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.MimeConstants;
import org.apache.xmlgraphics.io.Resource;
import org.apache.xmlgraphics.io.ResourceResolver;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

/**
 * Renders statement XML ({@code hbs-transmission}) to PDF through the legacy
 * XSL-FO stylesheets, as {@code dad/HbsStatementPdfFactory.getPDFTransmission}
 * did: XSLT {@code xsl/<TRANS_VERSION>/hbs-transmission.xsl} → FO → PDF.
 *
 * <p>Legacy ran Xalan + FOP 0.20.5; this runs the JDK's XSLT 1.0 processor and
 * Apache FOP 2.x with {@code xsl/support/hbs-fop.xconf} (Arial → Helvetica,
 * relaxed FO validation) and {@link Fop020CompatibilityFilter} between the two
 * steps for the FO that FOP 0.20 accepted and 2.x does not. The stylesheet version is the one the stored file
 * was written with ({@code HBS_FILE.HBS_TS_VERSION_ID → HBS_TS_VERSION.TRANS_VERSION}),
 * or the latest version for merged documents ({@code XmlConstants.CURRENT_XSL_VERSION}).
 *
 * <p>Nothing is loaded from outside the classpath: stylesheet imports resolve
 * inside {@code xsl/<version>/}, and FOP's only external resource,
 * {@code url(logo.gif)} in page-header.xsl, resolves to {@code xsl/support/}.
 */
@Component
@Slf4j
public class StatementPdfRenderer {

  /** TRANS_VERSION values look like V1_0, V1_0h, V999_0. */
  private static final Pattern VERSION = Pattern.compile("V\\d{1,3}_\\d{1,3}[a-z]?");
  private static final Pattern SUPPORT_FILE = Pattern.compile("[A-Za-z0-9_-]{1,40}\\.(gif|png|jpg)");
  private static final String XSL_ROOT = "xsl/";
  private static final String SUPPORT = "xsl/support/";
  private static final String XSL_SCHEME = "hbs-xsl";
  private static final URI FOP_BASE = URI.create("hbs-fop:/");

  private final FopFactory fopFactory;
  private final TransformerFactory transformerFactory;
  private final Map<String, Templates> templates = new ConcurrentHashMap<>();

  public StatementPdfRenderer() {
    this.fopFactory = buildFopFactory();
    this.transformerFactory = buildTransformerFactory();
  }

  /** True when {@code xsl/<version>/hbs-transmission.xsl} ships with the app. */
  public boolean supports(String transVersion) {
    return transVersion != null && VERSION.matcher(transVersion).matches()
        && classLoader().getResource(XSL_ROOT + transVersion + "/hbs-transmission.xsl") != null;
  }

  /** The statement XML rendered as a PDF with the given stylesheet version. */
  public byte[] render(String xml, String transVersion) {
    ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
    render(xml, transVersion, out);
    return out.toByteArray();
  }

  public void render(String xml, String transVersion, OutputStream out) {
    if (xml == null || xml.isBlank()) {
      throw new IllegalStateException("Can't write PDF to the output Stream if there is no data in the xmlReader");
    }
    Templates stylesheet = templates(transVersion);
    try {
      FOUserAgent agent = fopFactory.newFOUserAgent();
      agent.setProducer("Harvest Billing System");
      Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, agent, out);
      var transformer = stylesheet.newTransformer();
      Fop020CompatibilityFilter compat = new Fop020CompatibilityFilter();
      compat.setContentHandler(fop.getDefaultHandler());
      transformer.transform(new StreamSource(new StringReader(xml)), new SAXResult(compat));
    } catch (TransformerException | SAXException ex) {
      // Legacy: HBSEJBException "Error creating pdf document".
      throw new IllegalStateException("Error creating pdf document", ex);
    }
  }

  private Templates templates(String transVersion) {
    if (!supports(transVersion)) {
      throw new IllegalStateException("No statement stylesheet for transformation version " + transVersion);
    }
    return templates.computeIfAbsent(transVersion, v -> {
      String systemId = XSL_SCHEME + ":/" + v + "/hbs-transmission.xsl";
      try {
        return transformerFactory.newTemplates(classpathSource(systemId));
      } catch (TransformerConfigurationException ex) {
        throw new IllegalStateException("Statement stylesheet " + v + " does not compile", ex);
      }
    });
  }

  private TransformerFactory buildTransformerFactory() {
    // The JDK's built-in XSLT 1.0 processor, whatever else is on the classpath.
    TransformerFactory tf = TransformerFactory.newDefaultInstance();
    try {
      tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    } catch (TransformerConfigurationException ex) {
      throw new IllegalStateException(ex);
    }
    tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
    tf.setURIResolver(xslImports());
    return tf;
  }

  /** {@code <xsl:import href="pagelayout.xsl"/>} → the same version directory on the classpath. */
  private URIResolver xslImports() {
    return (href, base) -> {
      URI resolved = URI.create(base == null ? XSL_SCHEME + ":/" : base).resolve(href);
      if (!XSL_SCHEME.equals(resolved.getScheme())) {
        throw new TransformerException("Stylesheet import outside the statement stylesheets: " + href);
      }
      return classpathSource(resolved.toString());
    };
  }

  private Source classpathSource(String systemId) throws TransformerConfigurationException {
    String path = URI.create(systemId).getPath();
    if (path == null || path.contains("..") || !path.matches("/V\\d{1,3}_\\d{1,3}[a-z]?/[a-z-]+\\.xsl")) {
      throw new TransformerConfigurationException("Invalid statement stylesheet path " + systemId);
    }
    InputStream in = classLoader().getResourceAsStream(XSL_ROOT + path.substring(1));
    if (in == null) {
      throw new TransformerConfigurationException("Statement stylesheet not found: " + path);
    }
    StreamSource source = new StreamSource(in);
    source.setSystemId(systemId);
    return source;
  }

  private static FopFactory buildFopFactory() {
    ResourceResolver resolver = new ResourceResolver() {
      @Override
      public Resource getResource(URI uri) throws IOException {
        String path = uri.getPath();
        String name = path == null ? "" : path.substring(path.lastIndexOf('/') + 1);
        if (!"hbs-fop".equals(uri.getScheme()) || !SUPPORT_FILE.matcher(name).matches()) {
          throw new FileNotFoundException("Statement PDFs only load images from " + SUPPORT + ": " + uri);
        }
        InputStream in = classLoader().getResourceAsStream(SUPPORT + name);
        if (in == null) {
          throw new FileNotFoundException(SUPPORT + name);
        }
        return new Resource(in);
      }

      @Override
      public OutputStream getOutputStream(URI uri) throws IOException {
        throw new IOException("Statement rendering does not write files: " + uri);
      }
    };
    try (InputStream conf = classLoader().getResourceAsStream(SUPPORT + "hbs-fop.xconf")) {
      if (conf == null) {
        throw new IllegalStateException("Could NOT find FOP config file: " + SUPPORT + "hbs-fop.xconf");
      }
      return new FopConfParser(conf, FOP_BASE, resolver).getFopFactoryBuilder().build();
    } catch (IOException | SAXException ex) {
      throw new IllegalStateException("Problems with FOP config file: " + SUPPORT + "hbs-fop.xconf", ex);
    }
  }

  private static ClassLoader classLoader() {
    return StatementPdfRenderer.class.getClassLoader();
  }
}
