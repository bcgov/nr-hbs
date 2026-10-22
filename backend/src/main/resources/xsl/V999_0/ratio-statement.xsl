<?xml version="1.0"?>
<!--
  $RCSfile: ratio-statement.xsl,v $ 
  Copyright (c) 2001, B.C. Ministry of Forests. 
  All rights reserved. 

	Author:  Chris McCann
	Created: 2002-02-27
	Version: $Revision: 1.1 $ $Author: CWMcCann $ $Date: 2003/07/02 21:49:01 $

	Ratio Statement templates.
	-->
	<xsl:stylesheet 
		xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
		xmlns:fo="http://www.w3.org/1999/XSL/Format"
		xmlns:stmt="gov.bc.ca/forests/hbs/statement/v1"
		version="1.0">
		<xsl:output method="xml" indent="yes"/>  

		<!-- Master template for a collection of Ratio Statements -->
		<xsl:template match="ratio-statements">
			<xsl:apply-templates/>
		</xsl:template>

		<!-- Master template for a single Ratio Statement -->
		<xsl:template match="ratio-statement">
			<fo:page-sequence master-reference="statement" initial-page-number="1" force-page-count="no-force">
				<!-- Create the Page Border -->
				<xsl:call-template name="page-border"/>	

				<!-- Create the Page Header -->
				<xsl:call-template name="page-header">
					<xsl:with-param name="report-sub-type" select="''" />
					<xsl:with-param name="report-type" select="'Ratio Statement'" />
					<xsl:with-param name="report-number" select="header/statement-number" />
					<xsl:with-param name="end-of-report-id" select="generate-id(.)"/>
				</xsl:call-template>

				<!-- Create the Page Footer -->
				<xsl:call-template name="statement-footer"/>

				<xsl:comment>MAIN DOCUMENT</xsl:comment> 
				<fo:flow flow-name="xsl-region-body">

					<xsl:apply-templates select="header" mode="ratio"/>

					<xsl:apply-templates select="segregation-details" mode="ratio"/>
					<xsl:apply-templates select="compiled-samples" mode="ratio"/>
					<!-- used for proper page numbering.-->
					<fo:block><xsl:attribute name="id"><xsl:value-of select="generate-id(.)"/></xsl:attribute></fo:block>

				</fo:flow>
			</fo:page-sequence>
		</xsl:template>

		<!-- Ratio Statement Header -->
		<xsl:template match="header" mode="ratio">

			<xsl:call-template name="format-address">
				<xsl:with-param name="to" select="population-owner" />
				<xsl:with-param name="copy-to" select="stratum-owner" />
			</xsl:call-template>

			<fo:table table-layout="fixed" width="100%" border-collapse="collapse">
				<!-- Use proportional columns to ensure the entire page is used -->
				<fo:table-column column-width="proportional-column-width(1.5)"/>
				<fo:table-column column-width="proportional-column-width(2)"/>
				<fo:table-column column-width="proportional-column-width(1.7)"/>
				<fo:table-column column-width="proportional-column-width(2)"/> 

				<fo:table-header xsl:use-attribute-sets="section-header-text">
					<!-- Section Header -->
					<fo:table-row>
						<fo:table-cell xsl:use-attribute-sets="start-indent" end-indent="0.0in" text-align="start" number-columns-spanned="2">
							<fo:block xsl:use-attribute-sets="section-header-block">
								Summary Document:&#160;&#160;<xsl:value-of select="statement-number"/>
							</fo:block>
						</fo:table-cell>
						<fo:table-cell start-indent="0.0in" xsl:use-attribute-sets="end-indent" text-align="end" number-columns-spanned="2">
							<fo:block xsl:use-attribute-sets="section-header-block">
								Issue Date:&#160;&#160;<xsl:call-template name="format-date"><xsl:with-param name="date" select="issue-date" /></xsl:call-template>
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

				<fo:table-body xsl:use-attribute-sets="regular-text indent">
					<fo:table-row>
						<fo:table-cell><fo:block font-weight="bold" end-indent="0.0in">Population Region:</fo:block></fo:table-cell>
						<fo:table-cell><fo:block>&#160;<xsl:value-of select="population-region/@org-unit-name"/></fo:block></fo:table-cell>
						<fo:table-cell><fo:block font-weight="bold" end-indent="0.0in">Population District:</fo:block></fo:table-cell>
						<fo:table-cell><fo:block>&#160;<xsl:value-of select="population-district/@org-unit-name"/></fo:block></fo:table-cell>
					</fo:table-row>

					<fo:table-row>
						<fo:table-cell><fo:block font-weight="bold">Population Owner:</fo:block></fo:table-cell>
						<fo:table-cell>
							<fo:block>
								&#160;<xsl:value-of select="population-owner/client-number"/>&#160;<xsl:value-of select="population-owner/client-code"/>
							</fo:block>
						</fo:table-cell>

						<fo:table-cell><fo:block font-weight="bold">Stratum Owner:</fo:block></fo:table-cell>
						<fo:table-cell>
							<fo:block>
								&#160;<xsl:value-of select="stratum-owner/client-number"/>&#160;<xsl:value-of select="stratum-owner/client-code"/>
							</fo:block>
						</fo:table-cell>
					</fo:table-row>

					<fo:table-row>
						<fo:table-cell number-columns-spanned="4" xsl:use-attribute-sets="address-space-size">
							<fo:block> </fo:block>
						</fo:table-cell>
					</fo:table-row>

					<fo:table-row>
						<fo:table-cell><fo:block font-weight="bold" end-indent="0.0in">Pop/Srat/Yr:</fo:block></fo:table-cell>
						<fo:table-cell>
							<fo:block>
								&#160;<xsl:value-of select="population-number"/>-<xsl:value-of select="stratum-number"/>-<xsl:value-of select="substring(sampling-year, 2, 4)"/>
							</fo:block>
						</fo:table-cell>
						<fo:table-cell><fo:block font-weight="bold" end-indent="0.0in">YTD Samples:</fo:block></fo:table-cell>
						<fo:table-cell>
							<fo:block>
								&#160;<xsl:value-of select="ytd-sample-count"/>
							</fo:block>
						</fo:table-cell>
					</fo:table-row>

					<fo:table-row>
						<fo:table-cell><fo:block font-weight="bold" end-indent="0.0in">Stratum Name:</fo:block></fo:table-cell>
						<fo:table-cell>
							<fo:block>&#160;<xsl:value-of select="stratum-name"/>
							</fo:block>
						</fo:table-cell>
						<fo:table-cell><fo:block font-weight="bold" end-indent="0.0in">YTD Sample Weight (kg):</fo:block></fo:table-cell>
						<fo:table-cell><fo:block>&#160;<xsl:value-of select="ytd-sample-weight"/></fo:block></fo:table-cell>
					</fo:table-row>

					<fo:table-row>
						<fo:table-cell><fo:block font-weight="bold" end-indent="0.0in">Computation Period:</fo:block></fo:table-cell>

						<fo:table-cell>
							<fo:block>&#160;<xsl:call-template name="format-date"><xsl:with-param name="date" select="from-date"/></xsl:call-template>
								to 
								<xsl:call-template name="format-date"><xsl:with-param name="date" select="to-date"/></xsl:call-template></fo:block>
						</fo:table-cell>

						<fo:table-cell><fo:block font-weight="bold" end-indent="0.0in">YTD Sample Volume(m3):</fo:block></fo:table-cell>
						<fo:table-cell><fo:block>&#160;<xsl:call-template name="format-volume"><xsl:with-param name="volume" select="ytd-sample-volume"/></xsl:call-template></fo:block></fo:table-cell>
					</fo:table-row>

					<fo:table-row>
						<fo:table-cell><fo:block font-weight="bold" end-indent="0.0in">Ratio Effective Date:</fo:block></fo:table-cell>
						<fo:table-cell><fo:block>&#160;<xsl:call-template name="format-date"><xsl:with-param name="date" select="effective-date"/></xsl:call-template></fo:block></fo:table-cell>

						<fo:table-cell><fo:block font-weight="bold" end-indent="0.0in">YTD Sample Ratio(m3/tonne):</fo:block></fo:table-cell>
						<fo:table-cell><fo:block>&#160;<xsl:call-template name="format-ratio"><xsl:with-param name="ratio" select="ytd-sample-ratio"/></xsl:call-template></fo:block></fo:table-cell>
					</fo:table-row>
				</fo:table-body>
			</fo:table>
		</xsl:template>

		<!-- Set up Section header and columns for Segregation Details -->
		<xsl:template match="segregation-details" mode="ratio">
			<fo:table table-layout="fixed" width="100%" border-collapse="collapse" table-omit-header-at-break="false" xsl:use-attribute-sets="indent">
				<fo:table-column column-width="proportional-column-width(1.4)"/>  <!-- Species -->
				<fo:table-column column-width="proportional-column-width(1.0)"/>  <!-- Grade -->
				<fo:table-column column-width="proportional-column-width(1)"/>    <!-- Period Volume -->
				<fo:table-column column-width="proportional-column-width(1)"/>    <!-- YTD Volume -->
				<fo:table-column column-width="proportional-column-width(1)"/>    <!-- YTD Ratio -->
				<fo:table-column column-width="proportional-column-width(2.6)"/>  <!-- left over -->

				<fo:table-header xsl:use-attribute-sets="column-header" text-align="start">

					<!--Spacer -->
					<fo:table-row>
						<fo:table-cell number-columns-spanned="6"  xsl:use-attribute-sets="space-size">
							<fo:block> 
								&#160;
							</fo:block>
						</fo:table-cell>
					</fo:table-row>

					<!-- Section Header -->
					<fo:table-row>
						<fo:table-cell number-columns-spanned="6" >
							<fo:block xsl:use-attribute-sets="section-header-block section-header-text"> 
								Segregation Details
							</fo:block>
						</fo:table-cell>
					</fo:table-row>

					<!-- Rule -->
					<fo:table-row>
						<fo:table-cell number-columns-spanned="5"  xsl:use-attribute-sets="space-size">
							<fo:block display-align="after"> 
								<fo:leader xsl:use-attribute-sets="rule"/>
							</fo:block>
						</fo:table-cell>
					</fo:table-row>

					<fo:table-row>
						<fo:table-cell><fo:block end-indent="0.0in">Species</fo:block></fo:table-cell>
						<fo:table-cell><fo:block end-indent="0.0in" text-align="center">Grade</fo:block></fo:table-cell>
						<fo:table-cell><fo:block start-indent="0.0in" text-align="end">Period Volume (m3)</fo:block></fo:table-cell>
						<fo:table-cell><fo:block start-indent="0.0in" text-align="end">YTD Volume (m3)</fo:block></fo:table-cell>
						<fo:table-cell><fo:block start-indent="0.0in" text-align="end">YTD Ratio (m3/tonne)</fo:block></fo:table-cell>
						<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
					</fo:table-row>

					<fo:table-row line-height="0.5mm">
						<!-- Rule -->
						<fo:table-cell number-columns-spanned="5"  xsl:use-attribute-sets="space-size">
							<fo:block display-align="before"> 
								<fo:leader xsl:use-attribute-sets="rule"/>
							</fo:block>
						</fo:table-cell>
					</fo:table-row>

					<!-- Spacer -->
					<fo:table-row>
						<fo:table-cell number-columns-spanned="6"  xsl:use-attribute-sets="space-size">
							<fo:block> 
								&#160;
							</fo:block>
						</fo:table-cell>
					</fo:table-row>
				</fo:table-header>

				<fo:table-body xsl:use-attribute-sets="indent regular-text">
					<!-- get the details and sort them accordingly -->
					<xsl:apply-templates select="segregation-detail" mode="ratio">
						<xsl:sort select="@species"/>
						<xsl:sort select="@grade"/>
					</xsl:apply-templates>	
					<xsl:apply-templates select="segregation-totals" mode="ratio"/>
				</fo:table-body>
			</fo:table>
		</xsl:template>
		
		<!-- Segregation Details for the Ratio Statement -->
		<xsl:template match="segregation-detail" mode="ratio">
			<fo:table-row>
				<fo:table-cell><fo:block end-indent="0.0in"><xsl:value-of select="@species"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block end-indent="0.0in" text-align="center"><xsl:value-of select="@grade"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block start-indent="0.0in" text-align="end"><xsl:call-template name="format-volume"><xsl:with-param name="volume" select="@period-sample-volume"/></xsl:call-template></fo:block></fo:table-cell>
				<fo:table-cell><fo:block start-indent="0.0in" text-align="end"><xsl:call-template name="format-volume"><xsl:with-param name="volume" select="@ytd-sample-volume"/></xsl:call-template></fo:block></fo:table-cell>
				<fo:table-cell><fo:block start-indent="0.0in" text-align="end"><xsl:call-template name="format-ratio"><xsl:with-param name="ratio" select="@ytd-sample-ratio"/></xsl:call-template></fo:block></fo:table-cell>
				<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
			</fo:table-row>
		</xsl:template>

		<!-- Segregation Detail Totals for the Ratio Statement -->
		<xsl:template match="segregation-totals" mode="ratio">
			<!-- Rule -->
			<fo:table-row>
				<fo:table-cell number-columns-spanned="5"  xsl:use-attribute-sets="space-size">
					<fo:block display-align="after"> 
						<fo:leader xsl:use-attribute-sets="rule"/>
					</fo:block>
				</fo:table-cell>
			</fo:table-row>
			<fo:table-row font-weight="bold">
				<fo:table-cell><fo:block>Segregations: <xsl:value-of select="count(../segregation-detail)"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
				<fo:table-cell><fo:block start-indent="0.0in" text-align="end"><xsl:call-template name="format-volume"><xsl:with-param name="volume" select="@period-sample-volume"/></xsl:call-template></fo:block></fo:table-cell>
				<fo:table-cell><fo:block start-indent="0.0in" text-align="end"><xsl:call-template name="format-volume"><xsl:with-param name="volume" select="@ytd-sample-volume"/></xsl:call-template></fo:block></fo:table-cell>
				<fo:table-cell><fo:block start-indent="0.0in" text-align="end"><xsl:call-template name="format-ratio"><xsl:with-param name="ratio" select="@ytd-sample-ratio"/></xsl:call-template></fo:block></fo:table-cell>
				<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
			</fo:table-row>			
		</xsl:template>

		<!-- Setup Section and column titles for Compiled Samples -->
		<xsl:template match="compiled-samples" mode="ratio">
			<fo:table table-layout="fixed" width="100%" border-collapse="collapse" table-omit-header-at-break="false" xsl:use-attribute-sets="indent regular-text">
				<fo:table-column column-width="proportional-column-width(1)"/> <!-- Statement Number -->
				<fo:table-column column-width="proportional-column-width(0.6)"/> <!-- Scale Site -->
				<fo:table-column column-width="proportional-column-width(1.3)"/> <!-- Scale Date -->
				<fo:table-column column-width="proportional-column-width(0.9)"/> <!-- Scaler Licence -->
				<fo:table-column column-width="proportional-column-width(0.9)"/> <!-- Return Number -->
				<fo:table-column column-width="proportional-column-width(0.9)"/> <!-- Txn Type -->
				<fo:table-column column-width="proportional-column-width(0.9)"/> <!-- Return Category -->
				<fo:table-column column-width="proportional-column-width(1.2)"/> <!-- Weigh Slip Number -->
				<fo:table-column column-width="proportional-column-width(1.2)"/> <!-- LDS Number -->
				<fo:table-column column-width="proportional-column-width(1)"/> <!-- Timber Mark -->
				<fo:table-column column-width="proportional-column-width(1)"/> <!-- Sample Volume -->
				<fo:table-column column-width="proportional-column-width(1.0)"/> <!-- Sample Weight -->
				<fo:table-column column-width="proportional-column-width(1.1)"/> <!-- Sample Ratio -->

				<fo:table-header xsl:use-attribute-sets="column-header">

					<!-- Spacer -->
					<fo:table-row>
						<fo:table-cell number-columns-spanned="13"  xsl:use-attribute-sets="space-size">
							<fo:block> 
								&#160;
							</fo:block>
						</fo:table-cell>
					</fo:table-row>

					<!-- Section Header -->
					<fo:table-row>
						<fo:table-cell number-columns-spanned="13" >
							<fo:block xsl:use-attribute-sets="section-header-block section-header-text"> 
								Compiled Samples
							</fo:block>
						</fo:table-cell>
					</fo:table-row>

					<!-- Rule -->
					<fo:table-row>
						<fo:table-cell number-columns-spanned="13"  xsl:use-attribute-sets="space-size">
							<fo:block display-align="after"> 
								<fo:leader xsl:use-attribute-sets="rule"/>
							</fo:block>
						</fo:table-cell>
					</fo:table-row>

					<!-- Column Titles -->
					<fo:table-row>
						<fo:table-cell><fo:block display-align="after">&#160;<fo:block/>Statement Number</fo:block></fo:table-cell>
						<fo:table-cell><fo:block display-align="after">&#160;<fo:block/>Scale Site</fo:block></fo:table-cell>
						<fo:table-cell><fo:block display-align="after">&#160;<fo:block/>Scale Date</fo:block></fo:table-cell>
						<fo:table-cell><fo:block display-align="after">&#160;<fo:block/>Scaler Licence</fo:block></fo:table-cell>
						<fo:table-cell><fo:block display-align="after">&#160;<fo:block/>Return Number</fo:block></fo:table-cell>
						<fo:table-cell><fo:block display-align="after">&#160;<fo:block/>Txn Type</fo:block></fo:table-cell>
						<fo:table-cell><fo:block display-align="after">&#160;<fo:block/>Return Category</fo:block></fo:table-cell>
						<fo:table-cell><fo:block display-align="after">Weigh Slip Number</fo:block></fo:table-cell>
						<fo:table-cell><fo:block display-align="after">&#160;<fo:block/>LDS Number</fo:block></fo:table-cell>
						<fo:table-cell><fo:block display-align="after">&#160;<fo:block/>Timber Mark</fo:block></fo:table-cell>
						<fo:table-cell><fo:block text-align="end" display-align="after">Sample Volume (m3)</fo:block></fo:table-cell>
						<fo:table-cell><fo:block text-align="end" display-align="after">Sample Weight (kg)</fo:block></fo:table-cell>
						<fo:table-cell><fo:block text-align="end" display-align="after">Sample Ratio (m3/tonne)</fo:block></fo:table-cell>
					</fo:table-row>

					<!-- Rule -->
					<fo:table-row line-height="0.5mm">
						<fo:table-cell number-columns-spanned="13"  xsl:use-attribute-sets="space-size">
							<fo:block display-align="before"> 
								<fo:leader xsl:use-attribute-sets="rule"/>
							</fo:block>
						</fo:table-cell>
					</fo:table-row>

					<!-- Spacer -->
					<fo:table-row>
						<fo:table-cell number-columns-spanned="13"  xsl:use-attribute-sets="space-size">
							<fo:block> 
								&#160;
							</fo:block>
						</fo:table-cell>
					</fo:table-row>

				</fo:table-header>
				<fo:table-body xsl:use-attribute-sets="regular-text indent">
					<!-- get the compiled samples and sort them -->
					<xsl:apply-templates select="compiled-sample" mode="ratio">
						<xsl:sort select="@scale-site"/>
						<xsl:sort select="@scale-date"/>
						<xsl:sort select="@scaler-licence"/>
						<xsl:sort select="@return-number"/>
					</xsl:apply-templates>
					<xsl:apply-templates select="sample-totals" mode="ratio"/>
				</fo:table-body>
			</fo:table>
		</xsl:template>

		<!-- Compiled Sample for the Ratio Report -->
		<xsl:template match="compiled-sample" mode="ratio">
			<fo:table-row>
				<fo:table-cell><fo:block text-align="start"><xsl:value-of select="@statement-number"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block text-align="start"><xsl:value-of select="@scale-site"/></fo:block></fo:table-cell>
				<fo:table-cell>
					<fo:block>
						<xsl:call-template name="format-date"><xsl:with-param name="date" select="@scale-date" /></xsl:call-template>
					</fo:block>
				</fo:table-cell>
				<fo:table-cell><fo:block><xsl:value-of select="@scaler-licence"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block><xsl:value-of select="@return-number"/></fo:block></fo:table-cell>
				<fo:table-cell>
					<fo:block>
						<xsl:choose>
							<xsl:when test="@transaction-type='REP'">Replace</xsl:when>
							<xsl:when test="@transaction-type='ORG'">Original</xsl:when>
							<xsl:when test="@transaction-type='CAN'">Cancel</xsl:when>
						</xsl:choose>
					</fo:block>
				</fo:table-cell>
				<fo:table-cell><fo:block><xsl:value-of select="@return-number"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block><xsl:value-of select="@weigh-slip-number"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block><xsl:value-of select="@lds-number"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block><xsl:value-of select="@timber-mark"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block text-align="end"><xsl:call-template name="format-volume"><xsl:with-param name="volume" select="@sample-volume"/></xsl:call-template></fo:block></fo:table-cell>
				<fo:table-cell><fo:block text-align="end"><xsl:value-of select="@sample-weight"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block text-align="end"><xsl:call-template name="format-ratio"><xsl:with-param name="ratio" select="@sample-ratio"/></xsl:call-template></fo:block></fo:table-cell>
			</fo:table-row>
		</xsl:template>

		<!-- Compiled Sample totals -->
		<xsl:template match="sample-totals" mode="ratio">
			<!-- Rule -->
			<fo:table-row>
				<fo:table-cell number-columns-spanned="13"  xsl:use-attribute-sets="space-size">
					<fo:block display-align="after"> 
						<fo:leader xsl:use-attribute-sets="rule"/>
					</fo:block>
				</fo:table-cell>
			</fo:table-row>
			<fo:table-row xsl:use-attribute-sets="column-header">
				<fo:table-cell number-columns-spanned="10">
					<fo:block>Total for <xsl:value-of select="count(../compiled-sample)"/> compiled samples for this period:</fo:block>
				</fo:table-cell>
				<fo:table-cell><fo:block text-align="end"><xsl:call-template name="format-volume"><xsl:with-param name="volume" select="@sample-volume"/></xsl:call-template></fo:block></fo:table-cell>
				<fo:table-cell><fo:block text-align="end"><xsl:value-of select="@sample-weight"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block text-align="end"><xsl:call-template name="format-ratio"><xsl:with-param name="ratio" select="@sample-ratio"/></xsl:call-template></fo:block></fo:table-cell>
			</fo:table-row>
		</xsl:template>

	</xsl:stylesheet>
