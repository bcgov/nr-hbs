package ca.bc.gov.nrs.hbs.api.queries;

import java.util.ArrayDeque;
import java.util.Deque;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.XMLFilterImpl;

/**
 * Sits between the legacy statement stylesheets and FOP 2.x and repairs the FO
 * that FOP 0.20.5 accepted but FOP 2.x rejects, so the stylesheets themselves
 * stay byte-for-byte the legacy ones ({@code hbs-core/src/main/resources/xsl}).
 *
 * <ul>
 *   <li><b>Cells beyond the table's columns.</b> Rule rows such as the segregation
 *       totals leader use {@code number-columns-spanned="9"} in a 6-column table;
 *       0.20 drew them across the table, 2.x stops with "the number of cells in the
 *       row overflows the number of fo:table-columns". The span is clamped to the
 *       columns left in the row; a cell that starts past the last column is dropped
 *       (0.20 did not render it either).</li>
 *   <li><b>Empty block-containers.</b> The page border ({@code xslt-functions.xsl}
 *       {@code page-border}) is an empty {@code fo:block-container}; 2.x requires a
 *       block child, so an empty {@code fo:block} is added.</li>
 *   <li><b>{@code width="100%"} tables with indents.</b> Section tables carry
 *       {@code start-indent}/{@code end-indent} 0.125in and {@code width="100%"}; 0.20 fitted
 *       them between the indents, 2.x takes 100% of the parent and pushes the last column
 *       past the page border. The width is dropped, which makes FOP fill the space between
 *       the indents (the same 100% for tables without indents).</li>
 *   <li><b>Borders on side regions.</b> {@code pagelayout.xsl} puts
 *       {@code border="solid"} on the zero-width {@code xsl-region-end}; XSL 1.1
 *       forbids borders/padding on outer regions, so they are dropped (nothing was
 *       visible at zero extent).</li>
 * </ul>
 */
final class Fop020CompatibilityFilter extends XMLFilterImpl {

  static final String FO = "http://www.w3.org/1999/XSL/Format";

  /** Column bookkeeping for one fo:table (innermost on top). */
  private static final class TableState {
    int columns;
    int used;
  }

  private final Deque<TableState> tables = new ArrayDeque<>();
  /** One entry per open fo:block-container: has it had an element child yet. */
  private final Deque<boolean[]> containers = new ArrayDeque<>();
  private int skipDepth;

  @Override
  public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
    if (skipDepth > 0) {
      skipDepth++;
      return;
    }
    if (!containers.isEmpty()) {
      containers.peek()[0] = true;
    }
    Attributes out = atts;
    if (FO.equals(uri)) {
      switch (localName) {
        case "table" -> {
          tables.push(new TableState());
          if ("100%".equals(attr(atts, "width"))) {
            out = without(atts, "width");
          }
        }
        case "table-column" -> {
          if (!tables.isEmpty()) {
            tables.peek().columns += intAttr(atts, "number-columns-repeated", 1);
          }
        }
        case "table-row" -> {
          if (!tables.isEmpty()) {
            tables.peek().used = 0;
          }
        }
        case "table-cell" -> {
          TableState table = tables.peek();
          if (table != null && table.columns > 0) {
            if (table.used >= table.columns) {
              skipDepth = 1;
              return;
            }
            int span = intAttr(atts, "number-columns-spanned", 1);
            if (table.used + span > table.columns) {
              span = table.columns - table.used;
              out = withAttr(atts, "number-columns-spanned", Integer.toString(span));
            }
            table.used += span;
          }
        }
        case "block-container" -> containers.push(new boolean[] {false});
        case "region-start", "region-end", "region-before", "region-after" -> out = withoutBorders(atts);
        default -> {
          // passed through unchanged
        }
      }
    }
    super.startElement(uri, localName, qName, out);
  }

  @Override
  public void endElement(String uri, String localName, String qName) throws SAXException {
    if (skipDepth > 0) {
      skipDepth--;
      return;
    }
    if (FO.equals(uri)) {
      if ("table".equals(localName) && !tables.isEmpty()) {
        tables.pop();
      } else if ("block-container".equals(localName) && !containers.isEmpty()) {
        boolean hasChild = containers.pop()[0];
        if (!hasChild) {
          String prefix = qName.contains(":") ? qName.substring(0, qName.indexOf(':') + 1) : "";
          super.startElement(FO, "block", prefix + "block", new AttributesImpl());
          super.endElement(FO, "block", prefix + "block");
        }
      }
    }
    super.endElement(uri, localName, qName);
  }

  @Override
  public void characters(char[] ch, int start, int length) throws SAXException {
    if (skipDepth == 0) {
      super.characters(ch, start, length);
    }
  }

  @Override
  public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
    if (skipDepth == 0) {
      super.ignorableWhitespace(ch, start, length);
    }
  }

  @Override
  public void processingInstruction(String target, String data) throws SAXException {
    if (skipDepth == 0) {
      super.processingInstruction(target, data);
    }
  }

  private static int intAttr(Attributes atts, String name, int fallback) {
    String v = atts.getValue("", name);
    if (v == null) {
      v = atts.getValue(name);
    }
    try {
      return v == null ? fallback : Math.max(1, Integer.parseInt(v.trim()));
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }

  private static String attr(Attributes atts, String name) {
    String v = atts.getValue("", name);
    return v != null ? v : atts.getValue(name);
  }

  private static Attributes without(Attributes atts, String name) {
    AttributesImpl copy = new AttributesImpl(atts);
    int i = copy.getIndex("", name);
    if (i < 0) {
      i = copy.getIndex(name);
    }
    if (i >= 0) {
      copy.removeAttribute(i);
    }
    return copy;
  }

  private static Attributes withAttr(Attributes atts, String name, String value) {
    AttributesImpl copy = new AttributesImpl(atts);
    int i = copy.getIndex("", name);
    if (i < 0) {
      i = copy.getIndex(name);
    }
    if (i >= 0) {
      copy.setValue(i, value);
    } else {
      copy.addAttribute("", name, name, "CDATA", value);
    }
    return copy;
  }

  private static Attributes withoutBorders(Attributes atts) {
    AttributesImpl copy = new AttributesImpl(atts);
    for (int i = copy.getLength() - 1; i >= 0; i--) {
      String n = copy.getLocalName(i).isEmpty() ? copy.getQName(i) : copy.getLocalName(i);
      if (n.startsWith("border") || n.startsWith("padding")) {
        copy.removeAttribute(i);
      }
    }
    return copy;
  }
}
