<?xml version="1.0"?>
<!--
  $RCSfile: weight-scale-invoice.xsl,v $ 
  Copyright (c) 2001, B.C. Ministry of Forests. 
  All rights reserved. 

	Author:  Chris McCann
	Created: 2002-02-27
	Version: $Revision: 1.2 $ $Author: CWMcCann $ $Date: 2003/08/06 22:07:13 $

  Weight	Scale Invoice templates.
	-->
<xsl:stylesheet 
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	xmlns:fo="http://www.w3.org/1999/XSL/Format" 
	xmlns:stmt="gov.bc.ca/forests/hbs/statement/v1"
	version="1.0">
	<xsl:output method="xml" indent="yes"/>  

		<!-- Master template for a collection of weight scale invoices -->
	<xsl:template match="stmt:weight-scale-invoices">
		<xsl:apply-templates/>
	</xsl:template>

	<!-- Master template for a single weight scale invoice -->
	<xsl:template match="stmt:weight-scale-invoice">
		<fo:page-sequence master-reference="invoice" initial-page-number="1" force-page-count="no-force">
			<!-- Create the Page Border -->
			<xsl:call-template name="page-border"/>	

			<!-- Create the Page Header -->
			<xsl:call-template name="page-header">
				<xsl:with-param name="report-sub-type" select="'Weight Scale'" />
				<xsl:with-param name="report-type" select="'Invoice'" />
				<xsl:with-param name="report-number" select="stmt:header/stmt:statement-number" />
				<xsl:with-param name="end-of-report-id" select="generate-id(.)"/>
			</xsl:call-template>

			<!-- Create the Page Footer -->
			<xsl:call-template name="statement-footer">
				<xsl:with-param name="fine-print" select="stmt:fine-print" />
			</xsl:call-template> 


			<xsl:comment>MAIN DOCUMENT</xsl:comment> 
			<fo:flow flow-name="xsl-region-body">

				<xsl:apply-templates select="stmt:header" mode="weight-invoice"/>
				<xsl:apply-templates select="stmt:segregation-details" mode="weight-invoice"/>
				<xsl:apply-templates select="stmt:detail-documents" mode="weight-invoice"/>
				<xsl:apply-templates select="stmt:notations"/>
				<!-- used for proper page numbering.-->
				<fo:block><xsl:attribute name="id"><xsl:value-of select="generate-id(.)"/></xsl:attribute></fo:block>
			</fo:flow>
		</fo:page-sequence>
	</xsl:template>

	<!-- Weight Scale Invoice Header -->
	<xsl:template match="stmt:header" mode="weight-invoice">
		<xsl:call-template name="format-address">
			<xsl:with-param name="to" select="stmt:invoice-to" />
			<xsl:with-param name="copy-to" select="stmt:copy-to" />
		</xsl:call-template>

		<fo:table table-layout="fixed" width="100%" border-collapse="collapse">
			<!-- Use proportional columns to ensure the entire page is used -->
			<fo:table-column column-width="proportional-column-width(1)"/>
			<fo:table-column column-width="proportional-column-width(2)"/>
			<fo:table-column column-width="proportional-column-width(1)"/>
			<fo:table-column column-width="proportional-column-width(2)"/> 

			<fo:table-header xsl:use-attribute-sets="section-header-text">
				<!-- Section header -->
				<fo:table-row>
					<fo:table-cell xsl:use-attribute-sets="start-indent" end-indent="0.0in" text-align="start" number-columns-spanned="2">
						<fo:block xsl:use-attribute-sets="section-header-block">
							Summary Document:&#160;&#160;<xsl:value-of select="stmt:summary-doc-number"/>
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
					<fo:table-cell><fo:block font-weight="bold">Region Havested:</fo:block></fo:table-cell>
					<fo:table-cell><fo:block>&#160;<xsl:value-of select="stmt:region-harvested/@org-unit-name"/></fo:block></fo:table-cell>
					<fo:table-cell><fo:block font-weight="bold">District Harvested:</fo:block></fo:table-cell>
					<fo:table-cell><fo:block>&#160;<xsl:value-of select="stmt:district-harvested/@org-unit-name"/></fo:block></fo:table-cell>
				</fo:table-row>

				<fo:table-row>
					<fo:table-cell><fo:block font-weight="bold">Region Scaled:</fo:block></fo:table-cell>
					<fo:table-cell><fo:block>&#160;<xsl:value-of select="stmt:region-scaled/@org-unit-name"/></fo:block></fo:table-cell>
					<fo:table-cell><fo:block font-weight="bold">District Scaled:</fo:block></fo:table-cell>
					<fo:table-cell><fo:block>&#160;<xsl:value-of select="stmt:district-scaled/@org-unit-name"/></fo:block></fo:table-cell>
				</fo:table-row>

				<fo:table-row>
					<fo:table-cell><fo:block font-weight="bold">Invoice To:</fo:block></fo:table-cell>
					<fo:table-cell>
						<fo:block>
							&#160;<xsl:value-of select="stmt:invoice-to/stmt:client-number"/>&#160;<xsl:value-of select="stmt:invoice-to/stmt:client-code"/>
						</fo:block>
					</fo:table-cell>

					<fo:table-cell><fo:block font-weight="bold">Copy To:</fo:block></fo:table-cell>
					<fo:table-cell>
						<fo:block>
							&#160;<xsl:value-of select="stmt:copy-to/stmt:client-number"/>&#160;<xsl:value-of select="stmt:copy-to/stmt:client-code"/>
						</fo:block>
					</fo:table-cell>
				</fo:table-row>

					
				<fo:table-row>
						<fo:table-cell number-columns-spanned="4" xsl:use-attribute-sets="address-space-size">
							<fo:block> </fo:block>
						</fo:table-cell>
				</fo:table-row>

				<fo:table-row>
					<fo:table-cell><fo:block font-weight="bold">Scale Date:</fo:block></fo:table-cell>
					<fo:table-cell>
						<fo:block>
							&#160;<xsl:if test="stmt:scale-period/@start"><xsl:call-template name="format-date"><xsl:with-param name="date" select="stmt:scale-period/@start"/></xsl:call-template> to </xsl:if><xsl:call-template name="format-date"><xsl:with-param name="date" select="stmt:scale-period/@end"/></xsl:call-template>
						</fo:block>
					</fo:table-cell>
					<fo:table-cell><fo:block font-weight="bold">Summary Type:</fo:block></fo:table-cell>
					<fo:table-cell>
						<fo:block>
							&#160;<xsl:value-of select="stmt:summary-type"/>
						</fo:block>
					</fo:table-cell>
				</fo:table-row>

				<fo:table-row>
					<fo:table-cell><fo:block font-weight="bold">Scale Site:</fo:block></fo:table-cell>
					<fo:table-cell>
						<fo:block>&#160;<xsl:value-of select="stmt:scale-site/@id"/> - <xsl:value-of select="stmt:scale-site/@name"/>
						</fo:block>
					</fo:table-cell>
					<fo:table-cell><fo:block font-weight="bold">Return Category:</fo:block></fo:table-cell>
					<fo:table-cell><fo:block>&#160;<xsl:value-of select="stmt:return-category/@description"/></fo:block></fo:table-cell>
				</fo:table-row>

				<fo:table-row>
					<fo:table-cell><fo:block font-weight="bold" end-indent="0.0in">Pop/Srat/Yr:</fo:block></fo:table-cell>
					<fo:table-cell>
						<fo:block>
							&#160;<xsl:value-of select="stmt:population-number"/>-<xsl:value-of select="stmt:stratum-number"/>-<xsl:value-of select="substring(stmt:sampling-year, 2, 4)"/>
						</fo:block>
					</fo:table-cell>
					<fo:table-cell><fo:block font-weight="bold">Kind of Charge:</fo:block></fo:table-cell>
					<fo:table-cell><fo:block>&#160;<xsl:value-of select="stmt:kind-of-charge"/></fo:block></fo:table-cell>
				</fo:table-row>

				<fo:table-row>
					<fo:table-cell><fo:block font-weight="bold">Timber Mark:</fo:block></fo:table-cell>

					<fo:table-cell><fo:block>
							<xsl:choose>
								<xsl:when	test="stmt:deck-id">
									<fo:table table-layout="fixed" width="100%" border-collapse="collapse" >
										<fo:table-column column-width="proportional-column-width(1)"/>
										<fo:table-column column-width="proportional-column-width(2)"/>
										<fo:table-column column-width="proportional-column-width(2)"/>
										<fo:table-body>
											<fo:table-row>
												<fo:table-cell><fo:block>&#160;<xsl:value-of select="stmt:timber-mark"/></fo:block></fo:table-cell>
												<fo:table-cell><fo:block font-weight="bold">Deck ID:</fo:block></fo:table-cell>
												<fo:table-cell><fo:block>&#160;<xsl:value-of select="stmt:deck-id"/></fo:block></fo:table-cell>
											</fo:table-row>
										</fo:table-body>
									</fo:table>
								</xsl:when>	

								<xsl:when	test="cut-block">
									<fo:table table-layout="fixed" width="100%" border-collapse="collapse">
										<fo:table-column column-width="proportional-column-width(1)"/>
										<fo:table-column column-width="proportional-column-width(2)"/>
										<fo:table-column column-width="proportional-column-width(2)"/>
										<fo:table-body>
											<fo:table-row>
												<fo:table-cell><fo:block>&#160;<xsl:value-of select="stmt:timber-mark"/></fo:block></fo:table-cell>
												<fo:table-cell><fo:block font-weight="bold">Cut Block:</fo:block></fo:table-cell>
												<fo:table-cell><fo:block>&#160;<xsl:value-of select="stmt:cut-block"/></fo:block></fo:table-cell>
											</fo:table-row>
										</fo:table-body>
									</fo:table>
								</xsl:when>

								<xsl:otherwise>
									&#160;<xsl:value-of select="stmt:timber-mark"/>
								</xsl:otherwise>

							</xsl:choose>
						</fo:block>
					</fo:table-cell>
					<fo:table-cell><fo:block font-weight="bold">Transaction Type:</fo:block></fo:table-cell>
					<fo:table-cell><fo:block>&#160;<xsl:call-template name="format-tran-type"><xsl:with-param name="type" select="stmt:transaction-type"/></xsl:call-template></fo:block></fo:table-cell>
				</fo:table-row>

				<fo:table-row>
					<fo:table-cell><fo:block font-weight="bold">License:</fo:block></fo:table-cell>
					<fo:table-cell><fo:block>&#160;<xsl:value-of select="stmt:license"/></fo:block></fo:table-cell>
					<fo:table-cell><fo:block font-weight="bold">Statement Cycle:</fo:block></fo:table-cell>
					<fo:table-cell><fo:block>&#160;<xsl:value-of select="stmt:statement-cycle"/></fo:block></fo:table-cell>
				</fo:table-row>

				<fo:table-row>
					<fo:table-cell><fo:block font-weight="bold">Cutting Permit:</fo:block></fo:table-cell>
					<fo:table-cell><fo:block>&#160;<xsl:value-of select="stmt:cutting-permit"/></fo:block></fo:table-cell>
					<fo:table-cell><fo:block font-weight="bold">Net Weight:</fo:block></fo:table-cell>
					<fo:table-cell><fo:block>&#160;<xsl:call-template name="format-weight"><xsl:with-param name="weight" select="stmt:total-weight"/></xsl:call-template> kg</fo:block></fo:table-cell>
				</fo:table-row>

				<fo:table-row>
					<fo:table-cell><fo:block font-weight="bold">Previous Invoice:</fo:block></fo:table-cell>
					<fo:table-cell><fo:block>&#160;<xsl:value-of select="stmt:prev-statement-number"/></fo:block></fo:table-cell>
					<fo:table-cell><fo:block font-weight="bold">YTD Weight:</fo:block></fo:table-cell>
					<fo:table-cell><fo:block>&#160;<xsl:call-template name="format-weight"><xsl:with-param name="weight" select="stmt:ytd-weight"/></xsl:call-template> kg</fo:block></fo:table-cell>
				</fo:table-row>


				<!-- Rule -->
				<fo:table-row>
					<fo:table-cell number-columns-spanned="4">
						<fo:block>
							<fo:leader xsl:use-attribute-sets="rule"/>
						</fo:block>
					</fo:table-cell>
				</fo:table-row>

				<fo:table-row xsl:use-attribute-sets="section-header-text" >
					<fo:table-cell>
						<fo:block>Total Volume:</fo:block>
					</fo:table-cell>
					<fo:table-cell>
						<fo:block>&#160;<xsl:call-template name="format-volume"><xsl:with-param name="volume" select="stmt:total-volume"/></xsl:call-template>m3</fo:block>
					</fo:table-cell>
					<fo:table-cell>
						<fo:block>Total Due:</fo:block>
					</fo:table-cell>
					<fo:table-cell>
						<fo:block text-align="end">$<xsl:call-template name="format-currency"><xsl:with-param name="amount" select="stmt:invoice-amount"/></xsl:call-template></fo:block>
					</fo:table-cell>
				</fo:table-row>
			</fo:table-body>
		</fo:table>
	</xsl:template>

	<!-- Set up the section header and column headers for the Segregations Details -->
	<xsl:template match="stmt:segregation-details" mode="weight-invoice">
		<fo:table table-layout="fixed" width="100%" border-collapse="collapse" table-omit-header-at-break="false" xsl:use-attribute-sets="indent regular-text">
			<fo:table-column column-width="proportional-column-width(1.2)"/> <!-- Species -->
			<fo:table-column column-width="proportional-column-width(0.7)"/> <!-- Grade -->
			<fo:table-column column-width="proportional-column-width(0.5)"/> <!-- Rate ($/m3) -->
			<fo:table-column column-width="proportional-column-width(1.0)"/> <!-- YTD Weight x Rate -->
			<fo:table-column column-width="proportional-column-width(1.0)"/> <!-- Ratio -->
			<fo:table-column column-width="proportional-column-width(0.8)"/> <!-- YTD Volume -->
			<fo:table-column column-width="proportional-column-width(0.8)"/> <!-- Additional Volume -->
			<fo:table-column column-width="proportional-column-width(0.8)"/> <!-- YTD Value -->
			<fo:table-column column-width="proportional-column-width(0.8)"/> <!-- Additional Value -->
			<fo:table-column column-width="proportional-column-width(0.4)"/> <!-- left over -->

			<fo:table-header xsl:use-attribute-sets="column-header">

				<!-- Spacer -->
				<fo:table-row>
					<fo:table-cell number-columns-spanned="10"  xsl:use-attribute-sets="space-size">
						<fo:block> 
							&#160;
						</fo:block>
					</fo:table-cell>
				</fo:table-row>
				
				<!-- Section Header -->
				<fo:table-row>
					<fo:table-cell number-columns-spanned="10" >
						<fo:block xsl:use-attribute-sets="section-header-block section-header-text"> 
							Segregation Details
						</fo:block>
					</fo:table-cell>
				</fo:table-row>

				<!-- Rule -->
				<fo:table-row>
					<fo:table-cell number-columns-spanned="9"  xsl:use-attribute-sets="space-size">
						<fo:block display-align="after"> 
							<fo:leader xsl:use-attribute-sets="rule"/>
						</fo:block>
					</fo:table-cell>
				</fo:table-row>

				<!-- Column Titles -->
				<fo:table-row>
					<fo:table-cell text-align="start"><fo:block>&#160;<fo:block/>&#160;<fo:block/>Species</fo:block></fo:table-cell>
					<fo:table-cell text-align="start"><fo:block>&#160;<fo:block/>&#160;<fo:block/>Grade</fo:block></fo:table-cell>
					<fo:table-cell text-align="end"><fo:block>&#160;<fo:block/>Rate<fo:block/>($/m3)</fo:block></fo:table-cell>
					<fo:table-cell text-align="end"><fo:block end-indent="0.0in">YTD<fo:block/>Weight x Rate<fo:block/>(kg-$/m3)</fo:block></fo:table-cell>
					<fo:table-cell text-align="end"><fo:block>&#160;<fo:block/>Ratio<fo:block/>(m3/tonne)</fo:block></fo:table-cell>
					<fo:table-cell text-align="end"><fo:block>YTD<fo:block/>Volume<fo:block/>(m3)</fo:block></fo:table-cell>
					<fo:table-cell text-align="end"><fo:block>Additional<fo:block/>Volume<fo:block/>(m3)</fo:block></fo:table-cell>
					<fo:table-cell text-align="end"><fo:block>YTD<fo:block/>Value<fo:block/>($)</fo:block></fo:table-cell>
					<fo:table-cell text-align="end"><fo:block>Additional<fo:block/>Value<fo:block/>($)</fo:block></fo:table-cell>
					<fo:table-cell text-align="end"><fo:block>&#160;</fo:block></fo:table-cell>
				</fo:table-row>

				<fo:table-row line-height="0.5mm">
					<!-- Rule -->
					<fo:table-cell number-columns-spanned="9"  xsl:use-attribute-sets="space-size">
						<fo:block display-align="before"> 
							<fo:leader xsl:use-attribute-sets="rule"/>
						</fo:block>
					</fo:table-cell>
				</fo:table-row>

				<!-- Spacer -->
				<fo:table-row>
					<fo:table-cell number-columns-spanned="10"  xsl:use-attribute-sets="space-size">
						<fo:block> 
							&#160;
						</fo:block>
					</fo:table-cell>
				</fo:table-row>

			</fo:table-header>
			<fo:table-body xsl:use-attribute-sets="regular-text indent">
				<!-- Get all the details and sort them then display them -->
				<xsl:apply-templates select="stmt:segregation-detail" mode="weight-invoice">
					<xsl:sort select="@species"/>
					<xsl:sort select="@grade"/>
				</xsl:apply-templates>
				<!-- get the totals -->
				<xsl:apply-templates select="stmt:segregation-totals" mode="weight-invoice"/>
			</fo:table-body>
		</fo:table>
	</xsl:template>

	<!-- Segregation Details for Weight Scale Invoice -->
	<xsl:template match="stmt:segregation-detail" mode="weight-invoice">
		<fo:table-row>
			<fo:table-cell text-align="start"><fo:block><xsl:value-of select="@species"/></fo:block></fo:table-cell>
			<fo:table-cell text-align="start"><fo:block><xsl:value-of select="@grade"/></fo:block></fo:table-cell>
			<fo:table-cell text-align="end"><fo:block><xsl:call-template name="format-currency"><xsl:with-param name="amount" select="@stumpage-rate"/></xsl:call-template></fo:block></fo:table-cell>
			<fo:table-cell text-align="end"><fo:block><xsl:value-of select="@ytd-weight-times-rate"/></fo:block></fo:table-cell>
			<fo:table-cell text-align="end"><fo:block><xsl:call-template name="format-ratio"><xsl:with-param name="ratio" select="@segregation-ratio"/></xsl:call-template></fo:block></fo:table-cell>
			<fo:table-cell text-align="end"><fo:block><xsl:call-template name="format-volume"><xsl:with-param name="volume" select="@ytd-volume"/></xsl:call-template></fo:block></fo:table-cell>
			<fo:table-cell text-align="end"><fo:block><xsl:call-template name="format-volume"><xsl:with-param name="volume" select="@additional-volume"/></xsl:call-template></fo:block></fo:table-cell>
			<fo:table-cell text-align="end"><fo:block><xsl:call-template name="format-currency"><xsl:with-param name="amount" select="@ytd-value"/></xsl:call-template></fo:block></fo:table-cell>
			<fo:table-cell text-align="end"><fo:block><xsl:call-template name="format-currency"><xsl:with-param name="amount" select="@additional-value"/></xsl:call-template></fo:block></fo:table-cell>
			<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
		</fo:table-row>

	</xsl:template>

	<!-- Segregation Detail Totals for Weight Scale Invoice -->
	<xsl:template match="stmt:segregation-totals" mode="weight-invoice">
		<!-- Rule -->
		<fo:table-row>
			<fo:table-cell number-columns-spanned="9"  xsl:use-attribute-sets="space-size">
				<fo:block display-align="after"> 
					<fo:leader xsl:use-attribute-sets="rule"/>
				</fo:block>
			</fo:table-cell>
		</fo:table-row>

		<fo:table-row xsl:use-attribute-sets="column-header">
			<fo:table-cell><fo:block>Segregations: <xsl:value-of select="count(../stmt:segregation-detail)"/></fo:block></fo:table-cell>
			<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
			<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
			<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
			<fo:table-cell><fo:block text-align="end"><xsl:call-template name="format-ratio"><xsl:with-param name="ratio" select="@segregation-ratio"/></xsl:call-template></fo:block></fo:table-cell>
			<fo:table-cell><fo:block text-align="end"><xsl:call-template name="format-volume"><xsl:with-param name="volume" select="@ytd-volume"/></xsl:call-template></fo:block></fo:table-cell>
			<fo:table-cell><fo:block text-align="end"><xsl:call-template name="format-volume"><xsl:with-param name="volume" select="@additional-volume"/></xsl:call-template></fo:block></fo:table-cell>
			<fo:table-cell><fo:block text-align="end"><xsl:call-template name="format-currency"><xsl:with-param name="amount" select="@ytd-value"/></xsl:call-template></fo:block></fo:table-cell>
			<fo:table-cell><fo:block text-align="end"><xsl:call-template name="format-currency"><xsl:with-param name="amount" select="@additional-value"/></xsl:call-template></fo:block></fo:table-cell>
			<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
		</fo:table-row>
	</xsl:template>

	<!-- Setup the Section header and Column titles for Detail Documents for Weight Scale Invoice -->
	<xsl:template match="stmt:detail-documents" mode="weight-invoice">
		<fo:table table-layout="fixed" width="100%" border-collapse="collapse" table-omit-header-at-break="false" xsl:use-attribute-sets="indent regular-text">
			<fo:table-column column-width="proportional-column-width(1.2)"/> <!-- Document Number -->
			<fo:table-column column-width="proportional-column-width(1.0)"/> <!-- Scale Date -->
			<fo:table-column column-width="proportional-column-width(1.0)"/> <!-- Weigh Slip Number -->
			<fo:table-column column-width="proportional-column-width(1.0)"/> <!-- LDS Number -->
			<fo:table-column column-width="proportional-column-width(1.0)"/> <!-- Net Weight -->
			<fo:table-column column-width="proportional-column-width(1.0)"/> <!-- Additional Volume -->
			<fo:table-column column-width="proportional-column-width(1.0)"/> <!-- Additional Value-->
			<fo:table-column column-width="proportional-column-width(0.8)"/> <!-- left over -->

			<fo:table-header xsl:use-attribute-sets="column-header">

				<!-- Spacer -->
				<fo:table-row>
					<fo:table-cell number-columns-spanned="8"  xsl:use-attribute-sets="space-size">
						<fo:block> 
							&#160;
						</fo:block>
					</fo:table-cell>
				</fo:table-row>

				<!-- Section Header -->
				<fo:table-row>
					<fo:table-cell number-columns-spanned="8" >
						<fo:block xsl:use-attribute-sets="section-header-block section-header-text"> 
							Detail Documents
						</fo:block>
					</fo:table-cell>
				</fo:table-row>

				<!-- Rule -->
				<fo:table-row>
					<fo:table-cell number-columns-spanned="7"  xsl:use-attribute-sets="space-size">
						<fo:block display-align="after"> 
							<fo:leader xsl:use-attribute-sets="rule"/>
						</fo:block>
					</fo:table-cell>
				</fo:table-row>

				<!-- Column Titles -->
				<fo:table-row>
					<fo:table-cell><fo:block>Document<fo:block/>Number</fo:block></fo:table-cell>
					<fo:table-cell><fo:block>Scale<fo:block/>Date</fo:block></fo:table-cell>
					<fo:table-cell><fo:block>Weigh Slip<fo:block/>Number</fo:block></fo:table-cell>
					<fo:table-cell><fo:block>LDS<fo:block/>Number</fo:block></fo:table-cell>
					<fo:table-cell><fo:block text-align="end">Net<fo:block/>Weight (kg)</fo:block></fo:table-cell>
					<fo:table-cell><fo:block text-align="end">Additional<fo:block/>Volume (m3)</fo:block></fo:table-cell>
					<fo:table-cell><fo:block text-align="end">Additional<fo:block/>Value ($)</fo:block></fo:table-cell>
					<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
				</fo:table-row>
				
				<!-- Rule -->
				<fo:table-row line-height="0.5mm">
					<fo:table-cell number-columns-spanned="7"  xsl:use-attribute-sets="space-size">
						<fo:block display-align="before"> 
							<fo:leader xsl:use-attribute-sets="rule"/>
						</fo:block>
					</fo:table-cell>
				</fo:table-row>

				<!-- Spacer -->
				<fo:table-row>
					<fo:table-cell number-columns-spanned="8"  xsl:use-attribute-sets="space-size">
						<fo:block> 
							&#160;
						</fo:block>
					</fo:table-cell>
				</fo:table-row>

			</fo:table-header>
			<fo:table-body xsl:use-attribute-sets="regular-text indent">
				<!-- get the details and sort them accordingly -->
				<xsl:apply-templates select="stmt:detail-document" mode="weight-invoice">
					<xsl:sort select="@scale-date"/>
					<xsl:sort select="@weigh-slip-number"/>
				</xsl:apply-templates>
				<xsl:apply-templates select="stmt:detail-totals" mode="weight-invoice" />
			</fo:table-body>
		</fo:table>
	</xsl:template>

	<!-- Detail Documents for Weight Scale Invoice -->	
	<xsl:template match="stmt:detail-document" mode="weight-invoice">
		<fo:table-row>
			<fo:table-cell><fo:block text-align="start"><xsl:value-of select="@document-number"/></fo:block></fo:table-cell>
			<fo:table-cell>
				<fo:block>
					<xsl:call-template name="format-date"><xsl:with-param name="date" select="@scale-date" /></xsl:call-template>
				</fo:block>
			</fo:table-cell>
			<fo:table-cell><fo:block><xsl:value-of select="@weigh-slip-number"/></fo:block></fo:table-cell>
			<fo:table-cell><fo:block><xsl:value-of select="@load-description-slip"/></fo:block></fo:table-cell>
			<fo:table-cell><fo:block text-align="end"><xsl:call-template name="format-weight"><xsl:with-param name="weight" select="@net-weight"/></xsl:call-template></fo:block></fo:table-cell>
			<fo:table-cell><fo:block text-align="end"><xsl:call-template name="format-volume"><xsl:with-param name="volume" select="@additional-volume"/></xsl:call-template></fo:block></fo:table-cell>
			<fo:table-cell><fo:block text-align="end"><xsl:call-template name="format-currency"><xsl:with-param name="amount" select="@additional-value"/></xsl:call-template></fo:block></fo:table-cell>
			<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
		</fo:table-row>
	</xsl:template>

	<!-- Detail Document Totals for Weight Scale Invoice -->	
	<xsl:template match="stmt:detail-totals" mode="weight-invoice">
		<!-- Rule -->
		<fo:table-row>
			<fo:table-cell number-columns-spanned="9"  xsl:use-attribute-sets="space-size">
				<fo:block display-align="after"> 
					<fo:leader xsl:use-attribute-sets="rule"/>
				</fo:block>
			</fo:table-cell>
		</fo:table-row>

		<fo:table-row xsl:use-attribute-sets="column-header">
			<fo:table-cell><fo:block>Documents: <xsl:value-of select="count(../stmt:detail-document)"/></fo:block></fo:table-cell>
			<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
			<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
			<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
			<fo:table-cell><fo:block text-align="end"><xsl:call-template name="format-weight"><xsl:with-param name="weight" select="@net-weight"/></xsl:call-template></fo:block></fo:table-cell>
			<fo:table-cell><fo:block text-align="end"><xsl:call-template name="format-volume"><xsl:with-param name="volume" select="@additional-volume"/></xsl:call-template></fo:block></fo:table-cell>
			<fo:table-cell><fo:block text-align="end"><xsl:call-template name="format-currency"><xsl:with-param name="amount" select="@additional-value"/></xsl:call-template></fo:block></fo:table-cell>
			<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
		</fo:table-row>
	</xsl:template>

</xsl:stylesheet>
