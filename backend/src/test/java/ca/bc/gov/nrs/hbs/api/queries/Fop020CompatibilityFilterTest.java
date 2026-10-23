package ca.bc.gov.nrs.hbs.api.queries;

import java.io.StringReader;
import java.io.StringWriter;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import static org.assertj.core.api.Assertions.assertThat;

/** The FOP 0.20 → 2.x repairs applied between the legacy stylesheets and FOP. */
class Fop020CompatibilityFilterTest {

  private static String filter(String fo) throws Exception {
    SAXParserFactory spf = SAXParserFactory.newInstance();
    spf.setNamespaceAware(true);
    Fop020CompatibilityFilter filter = new Fop020CompatibilityFilter();
    filter.setParent(spf.newSAXParser().getXMLReader());
    var t = TransformerFactory.newInstance().newTransformer();
    t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
    StringWriter out = new StringWriter();
    t.transform(new SAXSource(filter, new InputSource(new StringReader(fo))), new StreamResult(out));
    return out.toString();
  }

  private static String fo(String body) {
    return "<fo:root xmlns:fo=\"" + Fop020CompatibilityFilter.FO + "\">" + body + "</fo:root>";
  }

  @Test
  void clampsSpansAndDropsCellsPastTheLastColumn() throws Exception {
    String out = filter(fo("""
        <fo:table width="100%" start-indent="0.125in"><fo:table-column/><fo:table-column/><fo:table-column/>
          <fo:table-body>
            <fo:table-row><fo:table-cell number-columns-spanned="9"><fo:block>rule</fo:block></fo:table-cell></fo:table-row>
            <fo:table-row><fo:table-cell number-columns-spanned="2"><fo:block>a</fo:block></fo:table-cell>
              <fo:table-cell number-columns-spanned="2"><fo:block>b</fo:block></fo:table-cell>
              <fo:table-cell><fo:block>dropped</fo:block></fo:table-cell></fo:table-row>
            <fo:table-row><fo:table-cell><fo:block>
              <fo:table><fo:table-column number-columns-repeated="2"/><fo:table-body><fo:table-row>
                <fo:table-cell number-columns-spanned="2"><fo:block>nested</fo:block></fo:table-cell>
              </fo:table-row></fo:table-body></fo:table></fo:block></fo:table-cell>
              <fo:table-cell number-columns-spanned="2"><fo:block>after nested</fo:block></fo:table-cell></fo:table-row>
          </fo:table-body></fo:table>"""));
    assertThat(out).contains("number-columns-spanned=\"3\"><fo:block>rule")
        .contains("number-columns-spanned=\"1\"><fo:block>b")
        .doesNotContain("dropped")
        .contains("number-columns-spanned=\"2\"><fo:block>nested")
        .contains("number-columns-spanned=\"2\"><fo:block>after nested")
        .doesNotContain("width=\"100%\"")
        .contains("start-indent=\"0.125in\"");
  }

  @Test
  void fillsEmptyBlockContainersAndStripsSideRegionBorders() throws Exception {
    String out = filter(fo("""
        <fo:simple-page-master master-name="m"><fo:region-body/>
          <fo:region-end border="solid" padding="1pt" region-name="xsl-region-end" extent="0in"/></fo:simple-page-master>
        <fo:block-container position="absolute"></fo:block-container>
        <fo:block-container><fo:block>kept</fo:block></fo:block-container>"""));
    assertThat(out).contains("<fo:block-container position=\"absolute\"><fo:block/></fo:block-container>")
        .contains("<fo:block-container><fo:block>kept</fo:block></fo:block-container>")
        .doesNotContain("border=").doesNotContain("padding=")
        .contains("region-name=\"xsl-region-end\"");
  }
}
