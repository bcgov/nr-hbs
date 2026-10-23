<?xml version="1.0" ?>
<!--
  $RCSfile: page-header.xsl,v $ 
  Copyright (c) 2001, B.C. Ministry of Forests. 
  All rights reserved. 
  
  Author:  Chris McCann
  Created: 2002-02-27
	Version: $Revision: 1.1 $ $Author: CWMcCann $ $Date: 2003/08/12 19:58:48 $

	Page Header for both invoices and statements	
-->
<xsl:stylesheet 
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	xmlns:fo="http://www.w3.org/1999/XSL/Format"
	version="1.0">
	<xsl:output method="xml" indent="yes"/>

	<!--
		Named template to insert the page header for both invoices and statements
		@param report-type The type of report eg. "Volume Statement" or "Invoice"
		@param report-sub-type The sub type of the report eg. "Piece Scale" or "Weight Scale"
		@param report-number The Report Number that identifies the report
		@param end-of-report-id A marker id that is at the end of the report for page numbering
	-->	
	<xsl:template name="page-header">
		<xsl:param name="report-type"/>
		<xsl:param name="report-sub-type"/>
		<xsl:param name="report-number"/>
		<xsl:param name="end-of-report-id"/>

		<fo:static-content flow-name="xsl-region-before">
			<fo:table table-layout="fixed" width="100%" border-collapse="collapse">
				<fo:table-column column-width="proportional-column-width(1)"/>
				<fo:table-column column-width="proportional-column-width(1)"/>
				<fo:table-column column-width="proportional-column-width(1)"/>
				<fo:table-body xsl:use-attribute-sets="header-text" text-align="center">
					<fo:table-row >

						<fo:table-cell display-align="center">
							<fo:block>
								<fo:external-graphic src="url(logo.gif)" height="0.63in" width="2.2in"/>
							</fo:block>
						</fo:table-cell>

						<fo:table-cell display-align="center">
							<fo:block >
								Ministry of Forests
								<fo:block/>
								<xsl:value-of select="$report-sub-type"/> <fo:block/>
								<xsl:value-of select="$report-type"/>
							</fo:block>
						</fo:table-cell>

						<fo:table-cell display-align="center">
							<fo:block>
								<fo:table table-layout="fixed" width="100%" border-collapse="collapse">
									<fo:table-column column-width="proportional-column-width(1)"/>
									<fo:table-column column-width="proportional-column-width(1)"/>
									<fo:table-body end-indent="0.25in">

										<fo:table-row>
											<fo:table-cell text-align="start"><fo:block>
													<xsl:choose>
														<xsl:when	test="$report-type='Invoice'">Invoice:</xsl:when>
														<xsl:otherwise>Statement:</xsl:otherwise>
													</xsl:choose>
											</fo:block></fo:table-cell>
											<fo:table-cell text-align="end"><fo:block><xsl:value-of select="$report-number"/></fo:block></fo:table-cell>
										</fo:table-row>
										<fo:table-row>
											<fo:table-cell number-columns-spanned="2" text-align="end">
												<fo:block xsl:use-attribute-sets="header-page-number-text">
													Page <fo:page-number/> of <fo:page-number-citation><xsl:attribute name="ref-id"><xsl:value-of select="$end-of-report-id"/></xsl:attribute></fo:page-number-citation>&#160;
												</fo:block>
											</fo:table-cell>
										</fo:table-row>
									</fo:table-body>
								</fo:table>
							</fo:block>
						</fo:table-cell>
					</fo:table-row>
				</fo:table-body>
			</fo:table>
		</fo:static-content>
	</xsl:template>

</xsl:stylesheet>

