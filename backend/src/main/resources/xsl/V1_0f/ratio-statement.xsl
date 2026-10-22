<?xml version="1.0"?>
<!--
  $RCSfile: ratio-statement.xsl,v $ 
  Copyright (c) 2001, B.C. Ministry of Forests. 
  All rights reserved. 

	Author:  Chris McCann
	Created: 2002-02-27
	Version: $Revision: 1.2 $ $Author: icostanzo $ $Date: 2003/10/07 16:57:12 $

	Ratio Statement templates.
	-->
	<xsl:stylesheet 
		xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
		xmlns:fo="http://www.w3.org/1999/XSL/Format"
		xmlns:stmt="gov.bc.ca/forests/hbs/statement/v1"
		version="1.0">
		<xsl:output method="xml" indent="yes"/>  

		<!-- Master template for a collection of Ratio Statements -->
		<xsl:template match="stmt:ratio-statements">
			<xsl:apply-templates/>
		</xsl:template>

		<!-- Master template for a single Ratio Statement -->
		<xsl:template match="stmt:ratio-statement">
			<fo:page-sequence master-reference="statement" initial-page-number="1" force-page-count="no-force">
				<!-- Create the Page Border -->
				<xsl:call-template name="page-border"/>	

				<!-- Create the Page Header -->
				<xsl:call-template name="page-header">
					<xsl:with-param name="report-sub-type" select="''" />
					<xsl:with-param name="report-type" select="'Ratio Statement'" />
					<xsl:with-param name="report-number" select="stmt:header/stmt:statement-number" />
					<xsl:with-param name="end-of-report-id" select="generate-id(.)"/>
				</xsl:call-template>

				<!-- Create the Page Footer -->
				<xsl:call-template name="statement-footer">
					<xsl:with-param name="fine-print" select="stmt:fine-print" />
				</xsl:call-template> 


				<xsl:comment>MAIN DOCUMENT</xsl:comment> 
				<fo:flow flow-name="xsl-region-body">

					<xsl:apply-templates select="stmt:header" mode="ratio"/>

					<xsl:apply-templates select="stmt:segregation-details" mode="ratio"/>
					<xsl:apply-templates select="stmt:compiled-samples" mode="ratio"/>
					<!-- used for proper page numbering.-->
					<fo:block><xsl:attribute name="id"><xsl:value-of select="generate-id(.)"/></xsl:attribute></fo:block>

				</fo:flow>
			</fo:page-sequence>
		</xsl:template>

		<!-- Ratio Statement Header -->
		<xsl:template match="stmt:header" mode="ratio">

			<xsl:call-template name="format-address">
				<xsl:with-param name="to" select="stmt:population-owner" />
				<xsl:with-param name="copy-to" select="stmt:stratum-owner" />
			</xsl:call-template>

			<fo:table table-layout="fixed" width="100%" border-collapse="collapse">
				<!-- Use proportional columns to ensure the entire page is used -->
				<fo:table-column column-width="proportional-column-width(1)"/>
				<fo:table-column column-width="proportional-column-width(1.5)"/>
				<fo:table-column column-width="proportional-column-width(1.5)"/>
				<fo:table-column column-width="proportional-column-width(2)"/> 
				<fo:table-header xsl:use-attribute-sets="section-header-text">
					<!-- Section Header -->
					<fo:table-row>
						<fo:table-cell xsl:use-attribute-sets="start-indent" end-indent="0.0in" text-align="start" number-columns-spanned="2">
							<fo:block xsl:use-attribute-sets="section-header-block">
								Summary Document:&#160;&#160;<xsl:value-of select="stmt:statement-number"/>
							</fo:block>
						</fo:table-cell>
						<fo:table-cell start-indent="0.0in" xsl:use-attribute-sets="end-indent" text-align="end" number-columns-spanned="2">
							<fo:block xsl:use-attribute-sets="section-header-block">
								Issue Date:&#160;&#160;<xsl:call-template name="format-date"><xsl:with-param name="date" select="stmt:issue-date" /></xsl:call-template>
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
						<fo:table-cell><fo:block>&#160;<xsl:value-of select="stmt:population-region/@org-unit-name"/></fo:block></fo:table-cell>
						<fo:table-cell><fo:block font-weight="bold" end-indent="0.0in">Population District:</fo:block></fo:table-cell>
						<fo:table-cell><fo:block>&#160;<xsl:value-of select="stmt:population-district/@org-unit-name"/></fo:block></fo:table-cell>
					</fo:table-row>

					<fo:table-row>
						<fo:table-cell><fo:block font-weight="bold">Population Owner:</fo:block></fo:table-cell>
						<fo:table-cell>
							<fo:block>
								&#160;<xsl:value-of select="stmt:population-owner/stmt:client-number"/>&#160;<xsl:value-of select="stmt:population-owner/stmt:client-code"/>
							</fo:block>
						</fo:table-cell>

						<fo:table-cell><fo:block font-weight="bold">Stratum Owner:</fo:block></fo:table-cell>
						<fo:table-cell>
							<fo:block>
								&#160;<xsl:value-of select="stmt:stratum-owner/stmt:client-number"/>&#160;<xsl:value-of select="stmt:stratum-owner/stmt:client-code"/>
							</fo:block>
						</fo:table-cell>
					</fo:table-row>

					<fo:table-row>
						<fo:table-cell number-columns-spanned="4" xsl:use-attribute-sets="address-space-size">
							<fo:block> </fo:block>
						</fo:table-cell>
					</fo:table-row>

					<fo:table-row>
						<fo:table-cell><fo:block font-weight="bold" end-indent="0.0in">Pop/Strat/Yr:</fo:block></fo:table-cell>
						<fo:table-cell>
							<fo:block>
								&#160;<xsl:value-of select="stmt:population-number"/>-<xsl:value-of select="stmt:stratum-number"/>-<xsl:value-of select="substring(stmt:sampling-year, 3, 4)"/>
							</fo:block>
						</fo:table-cell>
						<fo:table-cell><fo:block font-weight="bold" end-indent="0.0in">YTD Samples:</fo:block></fo:table-cell>
						<fo:table-cell>
							<fo:block>
								&#160;<xsl:value-of select="stmt:ytd-sample-count"/>
							</fo:block>
						</fo:table-cell>
					</fo:table-row>

					<fo:table-row>
						<fo:table-cell><fo:block font-weight="bold" end-indent="0.0in">Stratum Name:</fo:block></fo:table-cell>
						<fo:table-cell>
							<fo:block>&#160;<xsl:value-of select="stmt:stratum-name"/>
							</fo:block>
						</fo:table-cell>
						<fo:table-cell><fo:block font-weight="bold" end-indent="0.0in">YTD Sample Weight (kg):</fo:block></fo:table-cell>
						<fo:table-cell><fo:block>&#160;<xsl:value-of select="stmt:ytd-sample-weight"/></fo:block></fo:table-cell>
					</fo:table-row>

					<fo:table-row>
						<fo:table-cell><fo:block font-weight="bold" end-indent="0.0in">Computation Period:</fo:block></fo:table-cell>

						<fo:table-cell>
							<fo:block>&#160;<xsl:call-template name="format-date"><xsl:with-param name="date" select="stmt:from-date"/></xsl:call-template>
								to 
								<xsl:call-template name="format-date"><xsl:with-param name="date" select="stmt:to-date"/></xsl:call-template></fo:block>
						</fo:table-cell>

						<fo:table-cell><fo:block font-weight="bold" end-indent="0.0in">YTD Sample Volume (m3):</fo:block></fo:table-cell>
						<fo:table-cell><fo:block>&#160;<xsl:call-template name="format-volume"><xsl:with-param name="volume" select="stmt:ytd-sample-volume"/></xsl:call-template></fo:block></fo:table-cell>
					</fo:table-row>

					<fo:table-row>
						<fo:table-cell><fo:block font-weight="bold" end-indent="0.0in">Ratio Effective Date:</fo:block></fo:table-cell>
						<fo:table-cell><fo:block>&#160;<xsl:call-template name="format-date"><xsl:with-param name="date" select="stmt:effective-date"/></xsl:call-template></fo:block></fo:table-cell>

						<fo:table-cell><fo:block font-weight="bold" end-indent="0.0in">YTD Sample Ratio (m3/tonne):</fo:block></fo:table-cell>
						<fo:table-cell><fo:block>&#160;<xsl:call-template name="format-ratio"><xsl:with-param name="ratio" select="stmt:ytd-sample-ratio"/></xsl:call-template></fo:block></fo:table-cell>
					</fo:table-row>
				</fo:table-body>
			</fo:table>
		</xsl:template>

		<!-- Set up Section header and columns for Segregation Details -->
		<xsl:template match="stmt:segregation-details" mode="ratio">
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
					<xsl:apply-templates select="stmt:segregation-detail" mode="ratio">
						<xsl:sort select="@species"/>
						<xsl:sort select="@grade"/>
					</xsl:apply-templates>	
					<xsl:apply-templates select="stmt:segregation-totals" mode="ratio"/>
				</fo:table-body>
			</fo:table>
		</xsl:template>
		
		<!-- Segregation Details for the Ratio Statement -->
		<xsl:template match="stmt:segregation-detail" mode="ratio">
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
		<xsl:template match="stmt:segregation-totals" mode="ratio">
			<!-- Rule -->
			<fo:table-row>
				<fo:table-cell number-columns-spanned="5"  xsl:use-attribute-sets="space-size">
					<fo:block display-align="after"> 
						<fo:leader xsl:use-attribute-sets="rule"/>
					</fo:block>
				</fo:table-cell>
			</fo:table-row>
			<fo:table-row font-weight="bold">
				<fo:table-cell><fo:block>Segregations: <xsl:value-of select="count(../stmt:segregation-detail)"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
				<fo:table-cell><fo:block start-indent="0.0in" text-align="end"><xsl:call-template name="format-volume"><xsl:with-param name="volume" select="@period-sample-volume"/></xsl:call-template></fo:block></fo:table-cell>
				<fo:table-cell><fo:block start-indent="0.0in" text-align="end"><xsl:call-template name="format-volume"><xsl:with-param name="volume" select="@ytd-sample-volume"/></xsl:call-template></fo:block></fo:table-cell>
				<fo:table-cell><fo:block start-indent="0.0in" text-align="end"><xsl:call-template name="format-ratio"><xsl:with-param name="ratio" select="@ytd-sample-ratio"/></xsl:call-template></fo:block></fo:table-cell>
				<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
			</fo:table-row>			
		</xsl:template>

		<!-- Setup Section and column titles for Compiled Samples -->
		<xsl:template match="stmt:compiled-samples" mode="ratio">
			<fo:table table-layout="fixed" width="100%" border-collapse="collapse" table-omit-header-at-break="false" xsl:use-attribute-sets="indent regular-text">
				<fo:table-column column-width="proportional-column-width(1)"/> <!-- Statement Number -->
				<fo:table-column column-width="proportional-column-width(0.6)"/> <!-- Scale Site -->
				<fo:table-column column-width="proportional-column-width(1.3)"/> <!-- Scale Date -->
				<fo:table-column column-width="proportional-column-width(0.9)"/> <!-- Scaler Licence -->
				<fo:table-column column-width="proportional-column-width(0.9)"/> <!-- Return Number -->
				<fo:table-column column-width="proportional-column-width(1.0)"/> <!-- Txn Type -->
				<fo:table-column column-width="proportional-column-width(0.9)"/> <!-- Return Category -->
				<fo:table-column column-width="proportional-column-width(1.2)"/> <!-- Weigh Slip Number -->
				<fo:table-column column-width="proportional-column-width(1.1)"/> <!-- LDS Number -->
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
					<xsl:apply-templates select="stmt:compiled-sample" mode="ratio">
						<xsl:sort select="@scale-site"/>
						<xsl:sort select="@scale-date"/>
						<xsl:sort select="@scaler-license"/>
						<xsl:sort select="@return-number"/>
					</xsl:apply-templates>
					<xsl:apply-templates select="stmt:sample-totals" mode="ratio"/>
				</fo:table-body>
			</fo:table>
		</xsl:template>

		<!-- Compiled Sample for the Ratio Report -->
		<xsl:template match="stmt:compiled-sample" mode="ratio">
			<fo:table-row>
				<fo:table-cell><fo:block text-align="start"><xsl:value-of select="@statement-number"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block text-align="start"><xsl:value-of select="@scale-site"/></fo:block></fo:table-cell>
				<fo:table-cell>
					<fo:block>
						<xsl:call-template name="format-date"><xsl:with-param name="date" select="@scale-date" /></xsl:call-template>
					</fo:block>
				</fo:table-cell>
				<fo:table-cell><fo:block><xsl:value-of select="@scaler-license"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block><xsl:value-of select="@return-number"/></fo:block></fo:table-cell>
				<fo:table-cell>
					<fo:block>
						<xsl:call-template name="format-tran-type-ratio"><xsl:with-param name="type" select="@transaction-type"/></xsl:call-template>
					</fo:block>
				</fo:table-cell>
				<fo:table-cell><fo:block><xsl:value-of select="@return-category-code"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block><xsl:value-of select="@weigh-slip-number"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block><xsl:value-of select="@LDS-number"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block><xsl:value-of select="@timber-mark"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block text-align="end"><xsl:call-template name="format-volume"><xsl:with-param name="volume" select="@sample-volume"/></xsl:call-template></fo:block></fo:table-cell>
				<fo:table-cell><fo:block text-align="end"><xsl:value-of select="@sample-weight"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block text-align="end"><xsl:call-template name="format-ratio"><xsl:with-param name="ratio" select="@sample-ratio"/></xsl:call-template></fo:block></fo:table-cell>
			</fo:table-row>
		</xsl:template>

		<!-- Compiled Sample totals -->
		<xsl:template match="stmt:sample-totals" mode="ratio">
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
					<fo:block>Total for <xsl:value-of select="count(../stmt:compiled-sample)"/> compiled samples for this period:</fo:block>
				</fo:table-cell>
				<fo:table-cell><fo:block text-align="end"><xsl:call-template name="format-volume"><xsl:with-param name="volume" select="@sample-volume"/></xsl:call-template></fo:block></fo:table-cell>
				<fo:table-cell><fo:block text-align="end"><xsl:value-of select="@sample-weight"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
			</fo:table-row>
		</xsl:template>

	</xsl:stylesheet>
