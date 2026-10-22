<?xml version="1.0"?>
<!-- edited with XML Spy v4.3 U (http://www.xmlspy.com) by Chris McCann (Sierra Systems Group) -->
<!--
$RCSfile: hbs-transmission.xsl,v $ 
  Copyright (c) 2001, B.C. Ministry of Forests. 
  All rights reserved. 
  
  Author:  Chris McCann
  Created: 2002-02-27
	Version: $Revision: 1.2 $ $Author: CWMcCann $ $Date: 2004/09/07 22:34:42 $
  
	Top Level Stylesheet for transforming hbs-transmission xml files into PDF reports.
	Theory of operation:
		The XML data file gets transformed into a XSL:FO file to be rendered into a PDF.
		The stylesheet processor (currently Xalan) loads this file and all others in the
		declared in the import statements.  Then it matches the "/" template.
		From there each report type inside the data files gets matched and a new 
		page-sequence get created for the report.  Each of the sections in the report 
		has at least one matching template for it.  These commonally are:
			page-header (repeated on each page)
			page-footer (repeated on each page)
			page-border (repeated on each page)
			
			invoice-header (containing region/district information /mailing addresses, etc)
			Segregation-details (section header/ table definition)
				segregation-detail (each individual record)
				segregation-totals (totals for the last row)
			Detail-documents (section header/ table definition)
				detail-document (each individual record)
				detail-totals (totals for the last row)
			Notations (section header/ table definition)
				notation (each individual item)
			
		Each type of report has its own mode associated with it because of their differnces.		
-->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:fo="http://www.w3.org/1999/XSL/Format" xmlns:date="http://exslt.org/dates-and-times" xmlns:stmt="gov.bc.ca/forests/hbs/statement/v1" extension-element-prefixes="date" exclude-result-prefixes="xsl date" version="1.0">
	<!-- Imports for all the sub templates -->
	<xsl:import href="pagelayout.xsl"/>
	<xsl:import href="page-header.xsl"/>
	<xsl:import href="statement-footer.xsl"/>
	<xsl:import href="xslt-functions.xsl"/>
	<xsl:import href="report-attributes.xsl"/>
	<xsl:import href="piece-scale-invoice.xsl"/>
	<xsl:import href="piece-scale-volume-statement.xsl"/>
	<xsl:import href="ratio-statement.xsl"/>
	<xsl:import href="compiled-sample-statement.xsl"/>
	<xsl:import href="weight-scale-invoice.xsl"/>
	<xsl:import href="weight-scale-volume-statement.xsl"/>
	<xsl:output method="xml" indent="yes"/>
	<!-- 
		First Match
		This Template writes calls the page-layout template then applies all the other templates
	-->
	<xsl:template match="/">
		<fo:root xmlns:fo="http://www.w3.org/1999/XSL/Format">
			<xsl:call-template name="page-layout"/>
			<xsl:if test="/stmt:hbs-transmission/@client-number">
				<xsl:call-template name="title-page">
					<xsl:with-param name="transmission" select="/stmt:hbs-transmission"/>
				</xsl:call-template>
			</xsl:if>
			<xsl:if test="/stmt:hbs-transmission/stmt:hbs-request-header">
				<xsl:call-template name="request-header">
					<xsl:with-param name="requestheader" select="/stmt:hbs-transmission/stmt:hbs-request-header"/>
				</xsl:call-template>
			</xsl:if>
			<xsl:apply-templates/>
		</fo:root>
	</xsl:template>
	<!-- 
		Handles out putting the notations section in the invoices.  
		Since all the notation sections are the same have it top level
		Sets up the section header and the table for each notation.
	-->
	<xsl:template match="stmt:notations">
		<fo:table table-layout="fixed" width="100%" border-collapse="collapse" table-omit-header-at-break="false" xsl:use-attribute-sets="indent">
			<fo:table-column column-width="proportional-column-width(1)"/>
			<!-- Spacer -->
			<fo:table-header text-align="start">
				<fo:table-row>
					<fo:table-cell xsl:use-attribute-sets="space-size">
						<fo:block> 
							&#160;
						</fo:block>
					</fo:table-cell>
				</fo:table-row>
				<!-- Heading -->
				<fo:table-row>
					<fo:table-cell>
						<fo:block xsl:use-attribute-sets="section-header-block section-header-text"> 
							Notations
						</fo:block>
					</fo:table-cell>
				</fo:table-row>
				<!-- Spacer -->
				<fo:table-row>
					<fo:table-cell xsl:use-attribute-sets="space-size">
						<fo:block> 
							&#160;
						</fo:block>
					</fo:table-cell>
				</fo:table-row>
			</fo:table-header>
			<fo:table-body xsl:use-attribute-sets="regular-text indent">
				<xsl:apply-templates select="stmt:notation"/>
			</fo:table-body>
		</fo:table>
	</xsl:template>
	<!-- Write out the content of each notation tag -->
	<xsl:template match="stmt:notation">
		<fo:table-row>
			<fo:table-cell>
				<fo:block>
					<xsl:value-of select="."/>
					<xsl:apply-templates select="stmt:info"/>
				</fo:block>
			</fo:table-cell>
		</fo:table-row>
	</xsl:template>
	<xsl:template name="title-page">
		<xsl:param name="transmission"/>
		<fo:page-sequence master-reference="invoice" force-page-count="no-force" initial-page-number="1">
			<!-- Create the Page Header -->
			<xsl:call-template name="page-header">
				<xsl:with-param name="report-sub-type" select="'HBS Transmission'"/>
				<xsl:with-param name="report-type" select="'Cover Sheet'"/>
				<xsl:with-param name="report-number" select="/stmt:hbs-transmission/@transmission-id"/>
				<xsl:with-param name="end-of-report-id" select="generate-id(.)"/>
			</xsl:call-template>
			<fo:flow flow-name="xsl-region-body">
				<fo:table width="100%" table-layout="fixed" border-collapse="collapse">
					<fo:table-column column-width="proportional-column-width(1)"/>
					<fo:table-column column-width="proportional-column-width(1)"/>
					<fo:table-column column-width="proportional-column-width(1)"/>
					<fo:table-header xsl:use-attribute-sets="section-header-text">
						<!-- Section Header -->
						<fo:table-row>
							<fo:table-cell xsl:use-attribute-sets="start-indent" end-indent="0.0in" text-align="start">
								<fo:block xsl:use-attribute-sets="section-header-block">
										Recipient Type:&#160;&#160;<xsl:call-template name="format-recip-type">
										<xsl:with-param name="type" select="$transmission/@recipient-type"/>
									</xsl:call-template>
								</fo:block>
							</fo:table-cell>
							<fo:table-cell start-indent="0.0in" end-indent="0.0in" text-align="start">
								<fo:block xsl:use-attribute-sets="section-header-block">
										Client Number/Location:&#160;&#160;<xsl:value-of select="$transmission/@client-number"/>&#160;&#160;<xsl:value-of select="$transmission/@client-location-code"/>
								</fo:block>
							</fo:table-cell>
							<fo:table-cell start-indent="0.0in" xsl:use-attribute-sets="end-indent" text-align="end">
								<fo:block xsl:use-attribute-sets="section-header-block">
										Creation Date:&#160;&#160;<xsl:call-template name="format-date">
										<xsl:with-param name="date" select="$transmission/@date-created"/>
									</xsl:call-template>
								</fo:block>
							</fo:table-cell>
						</fo:table-row>
						<!-- Spacer -->
						<fo:table-row>
							<fo:table-cell number-columns-spanned="3" xsl:use-attribute-sets="space-size">
								<fo:block> 
										&#160;
									</fo:block>
							</fo:table-cell>
						</fo:table-row>
					</fo:table-header>
					<fo:table-body xsl:use-attribute-sets="regular-text indent">
						<fo:table-row>
							<!-- Nothing but the header for right now -->
							<fo:table-cell>
								<fo:block>&#160;</fo:block>
							</fo:table-cell>
							<fo:table-cell>
								<fo:block>&#160;</fo:block>
							</fo:table-cell>
							<fo:table-cell>
								<fo:block>&#160;</fo:block>
							</fo:table-cell>
						</fo:table-row>
					</fo:table-body>
				</fo:table>
				<fo:block line-height="2.5in">&#160;</fo:block>
				<fo:table table-layout="fixed" width="100%" border-collapse="collapse" table-omit-header-at-break="false" xsl:use-attribute-sets="indent">
					<fo:table-column column-width="proportional-column-width(2.0)"/>
					<!-- Statement Type -->
					<fo:table-column column-width="proportional-column-width(1.5)"/>
					<!-- First Statement -->
					<fo:table-column column-width="proportional-column-width(1.5)"/>
					<!-- Number of Statements -->
					<fo:table-column column-width="proportional-column-width(3.0)"/>
					<!-- left over -->
					<fo:table-header xsl:use-attribute-sets="column-header" text-align="start">
						<!--Spacer -->
						<fo:table-row>
							<fo:table-cell number-columns-spanned="4" xsl:use-attribute-sets="space-size">
								<fo:block> 
								&#160;
							</fo:block>
							</fo:table-cell>
						</fo:table-row>
						<!-- Section Header -->
						<fo:table-row>
							<fo:table-cell number-columns-spanned="4">
								<fo:block xsl:use-attribute-sets="section-header-block section-header-text"> 
								Transmission Details
							</fo:block>
							</fo:table-cell>
						</fo:table-row>
						<!-- Rule -->
						<fo:table-row>
							<fo:table-cell number-columns-spanned="3" xsl:use-attribute-sets="space-size">
								<fo:block display-align="after">
									<fo:leader xsl:use-attribute-sets="rule"/>
								</fo:block>
							</fo:table-cell>
						</fo:table-row>
						<fo:table-row>
							<fo:table-cell>
								<fo:block end-indent="0.0in">Type</fo:block>
							</fo:table-cell>
							<fo:table-cell>
								<fo:block end-indent="0.0in" text-align="center">First Statement</fo:block>
							</fo:table-cell>
							<fo:table-cell>
								<fo:block start-indent="0.0in" text-align="end">Number of Statements</fo:block>
							</fo:table-cell>
							<fo:table-cell>
								<fo:block>&#160;</fo:block>
							</fo:table-cell>
						</fo:table-row>
						<fo:table-row line-height="0.5mm">
							<!-- Rule -->
							<fo:table-cell number-columns-spanned="3" xsl:use-attribute-sets="space-size">
								<fo:block display-align="before">
									<fo:leader xsl:use-attribute-sets="rule"/>
								</fo:block>
							</fo:table-cell>
						</fo:table-row>
						<!-- Spacer -->
						<fo:table-row>
							<fo:table-cell number-columns-spanned="4" xsl:use-attribute-sets="space-size">
								<fo:block> 
								&#160;
							</fo:block>
							</fo:table-cell>
						</fo:table-row>
					</fo:table-header>
					<fo:table-body xsl:use-attribute-sets="indent regular-text">
						<xsl:if test="count(//stmt:piece-scale-invoice) > 0">
							<fo:table-row>
								<fo:table-cell>
									<fo:block end-indent="0.0in">
										<xsl:call-template name="format-stmt-type">
											<xsl:with-param name="type" select="'PSI'"/>
										</xsl:call-template>
									</fo:block>
								</fo:table-cell>
								<fo:table-cell>
									<fo:block end-indent="0.0in" text-align="center">
										<xsl:value-of select="//stmt:piece-scale-invoice[1]/stmt:header/stmt:statement-number"/>
									</fo:block>
								</fo:table-cell>
								<fo:table-cell>
									<fo:block start-indent="0.0in" text-align="end">
										<xsl:value-of select="count(//stmt:piece-scale-invoice)"/>
									</fo:block>
								</fo:table-cell>
								<fo:table-cell>
									<fo:block>&#160;</fo:block>
								</fo:table-cell>
							</fo:table-row>
						</xsl:if>
						<xsl:if test="count(//stmt:piece-scale-volume-statement) > 0">
							<fo:table-row>
								<fo:table-cell>
									<fo:block end-indent="0.0in">
										<xsl:call-template name="format-stmt-type">
											<xsl:with-param name="type" select="'PSV'"/>
										</xsl:call-template>
									</fo:block>
								</fo:table-cell>
								<fo:table-cell>
									<fo:block end-indent="0.0in" text-align="center">
										<xsl:value-of select="//stmt:piece-scale-volume-statement[1]/stmt:header/stmt:statement-number"/>
									</fo:block>
								</fo:table-cell>
								<fo:table-cell>
									<fo:block start-indent="0.0in" text-align="end">
										<xsl:value-of select="count(//stmt:piece-scale-volume-statement)"/>
									</fo:block>
								</fo:table-cell>
								<fo:table-cell>
									<fo:block>&#160;</fo:block>
								</fo:table-cell>
							</fo:table-row>
						</xsl:if>
						<xsl:if test="count(//stmt:weight-scale-invoice) > 0">
							<fo:table-row>
								<fo:table-cell>
									<fo:block end-indent="0.0in">
										<xsl:call-template name="format-stmt-type">
											<xsl:with-param name="type" select="'WSI'"/>
										</xsl:call-template>
									</fo:block>
								</fo:table-cell>
								<fo:table-cell>
									<fo:block end-indent="0.0in" text-align="center">
										<xsl:value-of select="//stmt:weight-scale-invoice[1]/stmt:header/stmt:statement-number"/>
									</fo:block>
								</fo:table-cell>
								<fo:table-cell>
									<fo:block start-indent="0.0in" text-align="end">
										<xsl:value-of select="count(//stmt:weight-scale-invoice)"/>
									</fo:block>
								</fo:table-cell>
								<fo:table-cell>
									<fo:block>&#160;</fo:block>
								</fo:table-cell>
							</fo:table-row>
						</xsl:if>
						<xsl:if test="count(//stmt:weight-scale-volume-statement) > 0">
							<fo:table-row>
								<fo:table-cell>
									<fo:block end-indent="0.0in">
										<xsl:call-template name="format-stmt-type">
											<xsl:with-param name="type" select="'WSV'"/>
										</xsl:call-template>
									</fo:block>
								</fo:table-cell>
								<fo:table-cell>
									<fo:block end-indent="0.0in" text-align="center">
										<xsl:value-of select="//stmt:weight-scale-volume-statement[1]/stmt:header/stmt:statement-number"/>
									</fo:block>
								</fo:table-cell>
								<fo:table-cell>
									<fo:block start-indent="0.0in" text-align="end">
										<xsl:value-of select="count(//stmt:weight-scale-volume-statement)"/>
									</fo:block>
								</fo:table-cell>
								<fo:table-cell>
									<fo:block>&#160;</fo:block>
								</fo:table-cell>
							</fo:table-row>
						</xsl:if>
						<xsl:if test="count(//stmt:compiled-sample-statement) > 0">
							<fo:table-row>
								<fo:table-cell>
									<fo:block end-indent="0.0in">
										<xsl:call-template name="format-stmt-type">
											<xsl:with-param name="type" select="'CSS'"/>
										</xsl:call-template>
									</fo:block>
								</fo:table-cell>
								<fo:table-cell>
									<fo:block end-indent="0.0in" text-align="center">
										<xsl:value-of select="//stmt:compiled-sample-statement[1]/stmt:header/stmt:statement-number"/>
									</fo:block>
								</fo:table-cell>
								<fo:table-cell>
									<fo:block start-indent="0.0in" text-align="end">
										<xsl:value-of select="count(//stmt:compiled-sample-statement)"/>
									</fo:block>
								</fo:table-cell>
								<fo:table-cell>
									<fo:block>&#160;</fo:block>
								</fo:table-cell>
							</fo:table-row>
						</xsl:if>
						<xsl:if test="count(//stmt:ratio-statement) > 0">
							<fo:table-row>
								<fo:table-cell>
									<fo:block end-indent="0.0in">
										<xsl:call-template name="format-stmt-type">
											<xsl:with-param name="type" select="'RS'"/>
										</xsl:call-template>
									</fo:block>
								</fo:table-cell>
								<fo:table-cell>
									<fo:block end-indent="0.0in" text-align="center">
										<xsl:value-of select="//stmt:ratio-statement[1]/stmt:header/stmt:statement-number"/>
									</fo:block>
								</fo:table-cell>
								<fo:table-cell>
									<fo:block start-indent="0.0in" text-align="end">
										<xsl:value-of select="count(//stmt:ratio-statement)"/>
									</fo:block>
								</fo:table-cell>
								<fo:table-cell>
									<fo:block>&#160;</fo:block>
								</fo:table-cell>
							</fo:table-row>
						</xsl:if>
						<fo:table-row>
							<fo:table-cell number-columns-spanned="3" xsl:use-attribute-sets="space-size">
								<fo:block display-align="after">
									<fo:leader xsl:use-attribute-sets="rule"/>
								</fo:block>
							</fo:table-cell>
						</fo:table-row>
						<fo:table-row font-weight="bold">
							<fo:table-cell>
								<fo:block>Total</fo:block>
							</fo:table-cell>
							<fo:table-cell>
								<fo:block>&#160;</fo:block>
							</fo:table-cell>
							<fo:table-cell>
								<fo:block start-indent="0.0in" text-align="end">
									<xsl:value-of select="count(//stmt:piece-scale-invoice | //stmt:piece-scale-volume-statement | //stmt:weight-scale-invoice | //stmt:weight-scale-volume-statement | //stmt:compiled-sample-statement | //stmt:ratio-statement)"/>
								</fo:block>
							</fo:table-cell>
							<fo:table-cell>
								<fo:block>&#160;</fo:block>
							</fo:table-cell>
						</fo:table-row>
					</fo:table-body>
				</fo:table>
				<xsl:choose>
					<xsl:when test="$transmission/@recipient-type='SEND_TO'">
						<fo:block-container height="1.25in" width="3.7in" position="absolute" left=".25in" top="0.80in">
							<fo:block start-indent="0.0in" end-indent="0.5in" xsl:use-attribute-sets="address-border address-text">
								<xsl:if test="//stmt:invoice-to[1]">
									<xsl:call-template name="print-address">
										<xsl:with-param name="address" select="//stmt:invoice-to[1]"/>
									</xsl:call-template>
								</xsl:if>
								<xsl:if test="not(//stmt:invoice-to[1])">
									<xsl:call-template name="print-address">
										<xsl:with-param name="address" select="//stmt:population-owner[1]"/>
									</xsl:call-template>
								</xsl:if>
							</fo:block>
						</fo:block-container>
					</xsl:when>
					<xsl:when test="$transmission/@recipient-type='COPY_TO'">
						<fo:block-container height="1.25in" width="3.7in" position="absolute" left="4.0in" top="0.80in">
							<fo:block start-indent="0.0in" end-indent="0.5in" xsl:use-attribute-sets="address-border address-text">
								<xsl:if test="//stmt:copy-to[1]">
									<xsl:call-template name="print-address">
										<xsl:with-param name="address" select="//stmt:copy-to[1]"/>
									</xsl:call-template>
								</xsl:if>
								<xsl:if test="not(//stmt:copy-to[1])">
									<xsl:call-template name="print-address">
										<xsl:with-param name="address" select="//stmt:stratum-owner[1]"/>
									</xsl:call-template>
								</xsl:if>
							</fo:block>
						</fo:block-container>
					</xsl:when>
				</xsl:choose>
			</fo:flow>
		</fo:page-sequence>
	</xsl:template>
	<xsl:template name="request-header">
		<xsl:param name="requestheader"/>
		<fo:page-sequence master-reference="invoice" force-page-count="no-force" initial-page-number="1">
			<!-- Create the Page Header -->
			<xsl:call-template name="page-header">
				<xsl:with-param name="report-sub-type" select="'Issued Statement'"/>
				<xsl:with-param name="report-type" select="'Query Request'"/>
				<xsl:with-param name="report-number" select="$requestheader/stmt:request-id"/>
				<xsl:with-param name="end-of-report-id" select="generate-id(.)"/>
			</xsl:call-template>
			<fo:flow flow-name="xsl-region-body">
				<fo:table width="100%" table-layout="fixed" border-collapse="collapse">
					<fo:table-column column-width="proportional-column-width(1)"/>
					<fo:table-column column-width="proportional-column-width(1)"/>
					<fo:table-column column-width="proportional-column-width(1)"/>
					<fo:table-header xsl:use-attribute-sets="section-header-text">
						<fo:table-row>
							<fo:table-cell xsl:use-attribute-sets="start-indent" end-indent="0.0in" text-align="start">
								<fo:block xsl:use-attribute-sets="section-header-block">
                                    Creation Date:&#160;&#160;
                                    <xsl:call-template name="format-date">
										<xsl:with-param name="date" select="$requestheader/stmt:date-created"/>
									</xsl:call-template>
								</fo:block>
							</fo:table-cell>
							<fo:table-cell start-indent="0.0in" end-indent="0.0in" text-align="start">
								<fo:block xsl:use-attribute-sets="section-header-block">
                                    &#160;&#160;
								</fo:block>
							</fo:table-cell>
							<fo:table-cell start-indent="0.0in" xsl:use-attribute-sets="end-indent" text-align="end">
								<fo:block xsl:use-attribute-sets="section-header-block">
                                    Creation Time:&#160;&#160;<xsl:value-of select="$requestheader/stmt:time-created"/>
								</fo:block>
							</fo:table-cell>
						</fo:table-row>
						<!-- Spacer -->
						<fo:table-row>
							<fo:table-cell number-columns-spanned="3" xsl:use-attribute-sets="space-size">
								<fo:block> 
										&#160;
									</fo:block>
							</fo:table-cell>
						</fo:table-row>
					</fo:table-header>
					<fo:table-body xsl:use-attribute-sets="regular-text indent">
						<fo:table-row>
							<fo:table-cell number-columns-spanned="3" xsl:use-attribute-sets="space-size">
								<fo:block> 
									&#160;
								</fo:block>
							</fo:table-cell>
						</fo:table-row>
					</fo:table-body>
				</fo:table>
				<xsl:if test="/stmt:hbs-request-header/stmt:requestor-email">
					<fo:block xsl:use-attribute-sets="regular-text indent">
						<fo:inline font-weight="bold">Requestor Email:&#160;&#160;</fo:inline>
						<xsl:value-of select="$requestheader/stmt:requestor-email"/>
					</fo:block>
				</xsl:if>
				<xsl:if test="$requestheader/stmt:request-parameters">
					<fo:block xsl:use-attribute-sets="regular-text indent">
						<fo:inline font-weight="bold">Requestor Parameters:&#160;&#160;</fo:inline>
						<xsl:value-of select="$requestheader/stmt:request-parameters"/>
					</fo:block>
				</xsl:if>
				<xsl:if test="$requestheader/stmt:requestor-relationship">
					<fo:block xsl:use-attribute-sets="regular-text indent">
						<fo:inline font-weight="bold">Requestor Relationship:&#160;&#160;</fo:inline>
						<xsl:value-of select="$requestheader/stmt:requestor-relationship"/>
					</fo:block>
				</xsl:if>
				<xsl:if test="$requestheader/stmt:requestor-client">
					<fo:block xsl:use-attribute-sets="regular-text indent">
						<fo:inline font-weight="bold">Requestor Client Number/Location:&#160;&#160;</fo:inline>
						<xsl:value-of select="$requestheader/stmt:requestor-client"/>&#160;&#160;<xsl:value-of select="$requestheader/stmt:requestor-client-location"/>
					</fo:block>
				</xsl:if>
				<xsl:if test="$requestheader/stmt:fine-print">
					<fo:block xsl:use-attribute-sets="regular-text indent">
						<fo:inline font-weight="bold">Disclaimer:&#160;&#160;</fo:inline>
						<xsl:value-of select="$requestheader/stmt:fine-print"/>
					</fo:block>
				</xsl:if>
				<fo:block xsl:use-attribute-sets="regular-text indent">
                    &#160;
                </fo:block>
				<fo:table table-layout="fixed" width="100%" border-collapse="collapse" table-omit-header-at-break="false" xsl:use-attribute-sets="indent">
					<fo:table-column column-width="proportional-column-width(2.0)"/>
					<!-- Statement Type -->
					<fo:table-column column-width="proportional-column-width(1.5)"/>
					<!-- First Statement -->
					<fo:table-column column-width="proportional-column-width(1.5)"/>
					<!-- Number of Statements -->
					<fo:table-column column-width="proportional-column-width(3.0)"/>
					<!-- left over -->
					<fo:table-header xsl:use-attribute-sets="column-header" text-align="start">
						<!--Spacer -->
						<fo:table-row>
							<fo:table-cell number-columns-spanned="4" xsl:use-attribute-sets="space-size">
								<fo:block> 
								&#160;
							</fo:block>
							</fo:table-cell>
						</fo:table-row>
						<!-- Section Header -->
						<fo:table-row>
							<fo:table-cell number-columns-spanned="4">
								<fo:block xsl:use-attribute-sets="section-header-block section-header-text"> 
								Transmission Details
							</fo:block>
							</fo:table-cell>
						</fo:table-row>
						<!-- Rule -->
						<fo:table-row>
							<fo:table-cell number-columns-spanned="3" xsl:use-attribute-sets="space-size">
								<fo:block display-align="after">
									<fo:leader xsl:use-attribute-sets="rule"/>
								</fo:block>
							</fo:table-cell>
						</fo:table-row>
						<fo:table-row>
							<fo:table-cell>
								<fo:block end-indent="0.0in">Type</fo:block>
							</fo:table-cell>
							<fo:table-cell>
								<fo:block end-indent="0.0in" text-align="center">First Statement</fo:block>
							</fo:table-cell>
							<fo:table-cell>
								<fo:block start-indent="0.0in" text-align="end">Number of Statements</fo:block>
							</fo:table-cell>
							<fo:table-cell>
								<fo:block>&#160;</fo:block>
							</fo:table-cell>
						</fo:table-row>
						<fo:table-row line-height="0.5mm">
							<!-- Rule -->
							<fo:table-cell number-columns-spanned="3" xsl:use-attribute-sets="space-size">
								<fo:block display-align="before">
									<fo:leader xsl:use-attribute-sets="rule"/>
								</fo:block>
							</fo:table-cell>
						</fo:table-row>
						<!-- Spacer -->
						<fo:table-row>
							<fo:table-cell number-columns-spanned="4" xsl:use-attribute-sets="space-size">
								<fo:block> 
								&#160;
							</fo:block>
							</fo:table-cell>
						</fo:table-row>
					</fo:table-header>
					<fo:table-body xsl:use-attribute-sets="indent regular-text">
						<xsl:if test="count(//stmt:piece-scale-invoice) > 0">
							<fo:table-row>
								<fo:table-cell>
									<fo:block end-indent="0.0in">
										<xsl:call-template name="format-stmt-type">
											<xsl:with-param name="type" select="'PSI'"/>
										</xsl:call-template>
									</fo:block>
								</fo:table-cell>
								<fo:table-cell>
									<fo:block end-indent="0.0in" text-align="center">
										<xsl:value-of select="//stmt:piece-scale-invoice[1]/stmt:header/stmt:statement-number"/>
									</fo:block>
								</fo:table-cell>
								<fo:table-cell>
									<fo:block start-indent="0.0in" text-align="end">
										<xsl:value-of select="count(//stmt:piece-scale-invoice)"/>
									</fo:block>
								</fo:table-cell>
								<fo:table-cell>
									<fo:block>&#160;</fo:block>
								</fo:table-cell>
							</fo:table-row>
						</xsl:if>
						<xsl:if test="count(//stmt:piece-scale-volume-statement) > 0">
							<fo:table-row>
								<fo:table-cell>
									<fo:block end-indent="0.0in">
										<xsl:call-template name="format-stmt-type">
											<xsl:with-param name="type" select="'PSV'"/>
										</xsl:call-template>
									</fo:block>
								</fo:table-cell>
								<fo:table-cell>
									<fo:block end-indent="0.0in" text-align="center">
										<xsl:value-of select="//stmt:piece-scale-volume-statement[1]/stmt:header/stmt:statement-number"/>
									</fo:block>
								</fo:table-cell>
								<fo:table-cell>
									<fo:block start-indent="0.0in" text-align="end">
										<xsl:value-of select="count(//stmt:piece-scale-volume-statement)"/>
									</fo:block>
								</fo:table-cell>
								<fo:table-cell>
									<fo:block>&#160;</fo:block>
								</fo:table-cell>
							</fo:table-row>
						</xsl:if>
						<xsl:if test="count(//stmt:weight-scale-invoice) > 0">
							<fo:table-row>
								<fo:table-cell>
									<fo:block end-indent="0.0in">
										<xsl:call-template name="format-stmt-type">
											<xsl:with-param name="type" select="'WSI'"/>
										</xsl:call-template>
									</fo:block>
								</fo:table-cell>
								<fo:table-cell>
									<fo:block end-indent="0.0in" text-align="center">
										<xsl:value-of select="//stmt:weight-scale-invoice[1]/stmt:header/stmt:statement-number"/>
									</fo:block>
								</fo:table-cell>
								<fo:table-cell>
									<fo:block start-indent="0.0in" text-align="end">
										<xsl:value-of select="count(//stmt:weight-scale-invoice)"/>
									</fo:block>
								</fo:table-cell>
								<fo:table-cell>
									<fo:block>&#160;</fo:block>
								</fo:table-cell>
							</fo:table-row>
						</xsl:if>
						<xsl:if test="count(//stmt:weight-scale-volume-statement) > 0">
							<fo:table-row>
								<fo:table-cell>
									<fo:block end-indent="0.0in">
										<xsl:call-template name="format-stmt-type">
											<xsl:with-param name="type" select="'WSV'"/>
										</xsl:call-template>
									</fo:block>
								</fo:table-cell>
								<fo:table-cell>
									<fo:block end-indent="0.0in" text-align="center">
										<xsl:value-of select="//stmt:weight-scale-volume-statement[1]/stmt:header/stmt:statement-number"/>
									</fo:block>
								</fo:table-cell>
								<fo:table-cell>
									<fo:block start-indent="0.0in" text-align="end">
										<xsl:value-of select="count(//stmt:weight-scale-volume-statement)"/>
									</fo:block>
								</fo:table-cell>
								<fo:table-cell>
									<fo:block>&#160;</fo:block>
								</fo:table-cell>
							</fo:table-row>
						</xsl:if>
						<xsl:if test="count(//stmt:compiled-sample-statement) > 0">
							<fo:table-row>
								<fo:table-cell>
									<fo:block end-indent="0.0in">
										<xsl:call-template name="format-stmt-type">
											<xsl:with-param name="type" select="'CSS'"/>
										</xsl:call-template>
									</fo:block>
								</fo:table-cell>
								<fo:table-cell>
									<fo:block end-indent="0.0in" text-align="center">
										<xsl:value-of select="//stmt:compiled-sample-statement[1]/stmt:header/stmt:statement-number"/>
									</fo:block>
								</fo:table-cell>
								<fo:table-cell>
									<fo:block start-indent="0.0in" text-align="end">
										<xsl:value-of select="count(//stmt:compiled-sample-statement)"/>
									</fo:block>
								</fo:table-cell>
								<fo:table-cell>
									<fo:block>&#160;</fo:block>
								</fo:table-cell>
							</fo:table-row>
						</xsl:if>
						<xsl:if test="count(//stmt:ratio-statement) > 0">
							<fo:table-row>
								<fo:table-cell>
									<fo:block end-indent="0.0in">
										<xsl:call-template name="format-stmt-type">
											<xsl:with-param name="type" select="'RS'"/>
										</xsl:call-template>
									</fo:block>
								</fo:table-cell>
								<fo:table-cell>
									<fo:block end-indent="0.0in" text-align="center">
										<xsl:value-of select="//stmt:ratio-statement[1]/stmt:header/stmt:statement-number"/>
									</fo:block>
								</fo:table-cell>
								<fo:table-cell>
									<fo:block start-indent="0.0in" text-align="end">
										<xsl:value-of select="count(//stmt:ratio-statement)"/>
									</fo:block>
								</fo:table-cell>
								<fo:table-cell>
									<fo:block>&#160;</fo:block>
								</fo:table-cell>
							</fo:table-row>
						</xsl:if>
						<fo:table-row>
							<fo:table-cell number-columns-spanned="3" xsl:use-attribute-sets="space-size">
								<fo:block display-align="after">
									<fo:leader xsl:use-attribute-sets="rule"/>
								</fo:block>
							</fo:table-cell>
						</fo:table-row>
						<fo:table-row font-weight="bold">
							<fo:table-cell>
								<fo:block>Total</fo:block>
							</fo:table-cell>
							<fo:table-cell>
								<fo:block>&#160;</fo:block>
							</fo:table-cell>
							<fo:table-cell>
								<fo:block start-indent="0.0in" text-align="end">
									<xsl:value-of select="count(//stmt:piece-scale-invoice | //stmt:piece-scale-volume-statement | //stmt:weight-scale-invoice | //stmt:weight-scale-volume-statement | //stmt:compiled-sample-statement | //stmt:ratio-statement)"/>
								</fo:block>
							</fo:table-cell>
							<fo:table-cell>
								<fo:block>&#160;</fo:block>
							</fo:table-cell>
						</fo:table-row>
					</fo:table-body>
				</fo:table>
			</fo:flow>
		</fo:page-sequence>
	</xsl:template>
</xsl:stylesheet>
