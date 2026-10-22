<?xml version="1.0"?>
<!--
  $RCSfile: hbs-transmission.xsl,v $ 
  Copyright (c) 2001, B.C. Ministry of Forests. 
  All rights reserved. 
  
  Author:  Chris McCann
  Created: 2002-02-27
	Version: $Revision: 1.1 $ $Author: CWMcCann $ $Date: 2003/08/12 19:58:48 $
  
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
<xsl:stylesheet 
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
	xmlns:fo="http://www.w3.org/1999/XSL/Format" 
	xmlns:date="http://exslt.org/dates-and-times"
	xmlns:stmt="gov.bc.ca/forests/hbs/statement/v1"
	extension-element-prefixes="date"
	exclude-result-prefixes="xsl date"
	version="1.0">

	<!-- Imports for all the sub templates -->
	<xsl:import href="pagelayout.xsl" />
	<xsl:import href="page-header.xsl" />
	<xsl:import href="statement-footer.xsl" />
	<xsl:import href="xslt-functions.xsl" />
	<xsl:import href="report-attributes.xsl" />
	<xsl:import href="piece-scale-invoice.xsl" />
	<xsl:import href="piece-scale-volume-statement.xsl" />
	<xsl:import href="ratio-statement.xsl" />
	<xsl:import href="compiled-sample-statement.xsl" />
	<xsl:import href="weight-scale-invoice.xsl" />
	<xsl:import href="weight-scale-volume-statement.xsl" />

	<xsl:output method="xml" indent="yes"/>  

	<!-- 
		First Match
		This Template writes calls the page-layout template then applies all the other templates
	-->
	<xsl:template match="/">
		<fo:root xmlns:fo="http://www.w3.org/1999/XSL/Format">
			<xsl:call-template name="page-layout" />
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
					<fo:table-cell >
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
				<xsl:apply-templates select="stmt:notation" />
			</fo:table-body>
		</fo:table>
	</xsl:template>

	<!-- Write out the content of each notation tag -->
	<xsl:template match="stmt:notation">
		<fo:table-row>
			<fo:table-cell>
				<fo:block>
					<xsl:value-of select="." />
					<xsl:apply-templates select="stmt:info"/>
				</fo:block>
			</fo:table-cell>
		</fo:table-row>		
	</xsl:template>

	<!-- 
		Prints out the time the transformation happened.
		Update!!!! Commented out the date:date-time call because it stopped working
		TODO fix this
	-->
	<xsl:template match="stmt:info">
		Transformed at <!--<xsl:value-of select="date:date-time()"/>-->
	</xsl:template>
</xsl:stylesheet>
