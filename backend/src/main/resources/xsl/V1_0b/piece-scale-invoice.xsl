<?xml version="1.0"?>
<!--
  $RCSfile: piece-scale-invoice.xsl,v $ 
  Copyright (c) 2001, B.C. Ministry of Forests. 
  All rights reserved. 

	Author:  Chris McCann
	Created: 2002-02-27
	Version: $Revision: 1.2 $ $Author: CWMcCann $ $Date: 2003/08/06 22:07:12 $

	Piece Scale Invoice templates.
	-->
	<xsl:stylesheet 
		xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
		xmlns:fo="http://www.w3.org/1999/XSL/Format" 
		xmlns:stmt="gov.bc.ca/forests/hbs/statement/v1"
		version="1.0">
		<xsl:output method="xml" indent="yes"/>  

		<!-- Master template for a collection of piece scale invoices -->
		<xsl:template match="stmt:piece-scale-invoices">
			<xsl:apply-templates/>
		</xsl:template>

		<!-- Master template for a single piece scale invoice -->
		<xsl:template match="stmt:piece-scale-invoice">
			<fo:page-sequence master-reference="invoice" initial-page-number="1" force-page-count="no-force">
				<!-- Create the Page Border -->
				<xsl:call-template name="page-border"/>

				<!-- Create the Page Header -->
				<xsl:call-template name="page-header">
					<xsl:with-param name="report-sub-type" select="'Piece Scale'" />
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
					<xsl:apply-templates select="stmt:header" mode="piece-invoice"/>

					<!-- 
					Each type of piece scale invoice shows different columns in the segreation
					details so use a different mode (and therefore template) for each type 

					Test the return category and call the proper segreation-detail template 
					-->
					<xsl:choose>
						<xsl:when test="stmt:header/stmt:return-category/@description[starts-with(.,'Field')]">
							<xsl:apply-templates select="stmt:segregation-details" mode="field"/>
						</xsl:when>

						<xsl:when test="stmt:header/stmt:return-category/@description[starts-with(.,'Primary')]">
							<xsl:apply-templates select="stmt:segregation-details" mode="primary"/>
						</xsl:when>

						<xsl:when test="stmt:header/stmt:return-category/@description[starts-with(.,'Cruise')]">
							<xsl:apply-templates select="stmt:segregation-details" mode="cruise"/>
						</xsl:when>
					
						<!-- other has the same fields as primary -->
						<xsl:when test="stmt:header/stmt:return-category/@description[starts-with(.,'Other')]">
							<xsl:apply-templates select="stmt:segregation-details" mode="primary"/>
						</xsl:when>

						<xsl:when test="stmt:header/stmt:return-category/@description[starts-with(.,'Beachcomb')]">
							<xsl:apply-templates select="stmt:segregation-details" mode="beachcomb"/>
						</xsl:when>

						<xsl:when test="stmt:header/stmt:return-category/@description[starts-with(.,'Waste')]">
							<xsl:apply-templates select="stmt:segregation-details" mode="waste"/>
						</xsl:when>

					</xsl:choose>

					<!-- All the details and notaions are the same among the different types-->
					<xsl:apply-templates select="stmt:detail-documents" mode="piece-scale"/>
					<xsl:apply-templates select="stmt:notations"/>
					<!-- used for proper page numbering.-->
					<fo:block><xsl:attribute name="id"><xsl:value-of select="generate-id(.)"/></xsl:attribute></fo:block>

				</fo:flow>
			</fo:page-sequence>
		</xsl:template>

		<!-- Piece Scale Invoice Header -->
		<xsl:template match="stmt:header" mode="piece-invoice">
			
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
						<fo:table-cell><fo:block font-weight="bold">Timber Mark:</fo:block></fo:table-cell>

						<!-- if the deck id or cut-block field is present display it, but only one-->
						<fo:table-cell><fo:block>
								<xsl:choose>
									<xsl:when	test="deck-id">
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

						<fo:table-cell><fo:block font-weight="bold">Kind of Charge:</fo:block></fo:table-cell>
						<fo:table-cell><fo:block>&#160;<xsl:value-of select="stmt:kind-of-charge"/></fo:block></fo:table-cell>
					</fo:table-row>

					<fo:table-row>
						<fo:table-cell><fo:block font-weight="bold">Licence:</fo:block></fo:table-cell>
						<fo:table-cell><fo:block>&#160;<xsl:value-of select="stmt:license"/></fo:block></fo:table-cell>
						<fo:table-cell><fo:block font-weight="bold">Transaction Type:</fo:block></fo:table-cell>
						<fo:table-cell><fo:block>&#160;<xsl:call-template name="format-tran-type"><xsl:with-param name="type" select="stmt:transaction-type"/></xsl:call-template></fo:block></fo:table-cell>
					</fo:table-row>

					<fo:table-row>
						<fo:table-cell><fo:block font-weight="bold">Cutting Permit:</fo:block></fo:table-cell>
						<fo:table-cell><fo:block>&#160;<xsl:value-of select="stmt:cutting-permit"/></fo:block></fo:table-cell>
						<fo:table-cell><fo:block font-weight="bold">Statement Cycle:</fo:block></fo:table-cell>
						<fo:table-cell><fo:block>&#160;<xsl:value-of select="stmt:statement-cycle"/></fo:block></fo:table-cell>
					</fo:table-row>

					<!-- Rule -->
					<fo:table-row>
						<fo:table-cell number-columns-spanned="4">
							<fo:block>
								<fo:leader xsl:use-attribute-sets="rule"/>
							</fo:block>
						</fo:table-cell>
					</fo:table-row>

					<!-- Main numbers for the invoice total volume and amount due -->
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

		<!-- Set up the section header and column headers for the details -->
		<xsl:template match="stmt:detail-documents" mode="piece-scale">
			<fo:table table-layout="fixed" width="100%" border-collapse="collapse" table-omit-header-at-break="false" xsl:use-attribute-sets="indent regular-text">
				<fo:table-column column-width="proportional-column-width(1.3)"/> <!-- Document Number -->
				<fo:table-column column-width="proportional-column-width(0.8)"/> <!-- Scale Date -->
				<fo:table-column column-width="proportional-column-width(0.6)"/> <!-- Scaler License -->
				<fo:table-column column-width="proportional-column-width(0.6)"/> <!-- Return Number -->
				<fo:table-column column-width="proportional-column-width(1.0)"/> <!-- Load Arrival Number -->
				<fo:table-column column-width="proportional-column-width(0.9)"/> <!-- LDS Number -->
				<fo:table-column column-width="proportional-column-width(0.5)"/> <!-- Log Count -->
				<fo:table-column column-width="proportional-column-width(0.9)"/> <!-- Volume -->
				<fo:table-column column-width="proportional-column-width(0.9)"/> <!-- Value -->
				<fo:table-column column-width="proportional-column-width(0.5)"/> <!-- left over -->

				<fo:table-header xsl:use-attribute-sets="column-header">
					<!-- Spacer -->
					<fo:table-row>
						<fo:table-cell number-columns-spanned="10"  xsl:use-attribute-sets="space-size">
							<fo:block> 
								&#160;
							</fo:block>
						</fo:table-cell>
					</fo:table-row>

					<!-- Section header -->
					<fo:table-row>
						<fo:table-cell number-columns-spanned="10" >
							<fo:block xsl:use-attribute-sets="section-header-block section-header-text"> 
								Document Details
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

					<!-- Column headers -->
					<fo:table-row>
						<fo:table-cell><fo:block>Document Number</fo:block></fo:table-cell>
						<fo:table-cell><fo:block>Scale Date</fo:block></fo:table-cell>
						<fo:table-cell><fo:block>Scaler License</fo:block></fo:table-cell>
						<fo:table-cell><fo:block>Return Number</fo:block></fo:table-cell>
						<fo:table-cell><fo:block>Load Arrival Number</fo:block></fo:table-cell>
						<fo:table-cell><fo:block>LDS Number</fo:block></fo:table-cell>
						<fo:table-cell><fo:block text-align="end">Log Count</fo:block></fo:table-cell>
						<fo:table-cell><fo:block text-align="end">Volume (m3)</fo:block></fo:table-cell>
						<fo:table-cell><fo:block text-align="end">Value ($)</fo:block></fo:table-cell>
						<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
					</fo:table-row>

					<!-- Rule -->
					<fo:table-row line-height="0.5mm">
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
					<xsl:apply-templates select="stmt:detail-document">
						<xsl:sort select="@scale-date"/>
						<xsl:sort select="@scaler-license"/>
						<xsl:sort select="@return-number"/>
					</xsl:apply-templates>
					<!-- get the totals -->
					<xsl:apply-templates select="stmt:detail-totals" />
				</fo:table-body>
			</fo:table>
		</xsl:template>

		<!-- Display the detail items -->
		<xsl:template match="stmt:detail-document">
			<fo:table-row>
				<fo:table-cell><fo:block text-align="start"><xsl:value-of select="@document-number"/></fo:block></fo:table-cell>
				<fo:table-cell>
					<fo:block>
						<xsl:call-template name="format-date"><xsl:with-param name="date" select="@scale-date" /></xsl:call-template>
					</fo:block>
				</fo:table-cell>
				<fo:table-cell><fo:block><xsl:value-of select="@scaler-license"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block><xsl:value-of select="@return-number"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block><xsl:value-of select="@load-arrival-number"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block><xsl:value-of select="@lds-number"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block text-align="end"><xsl:value-of select="@log-count"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block text-align="end"><xsl:call-template name="format-volume"><xsl:with-param name="volume" select="@volume"/></xsl:call-template></fo:block></fo:table-cell>
				<fo:table-cell><fo:block text-align="end"><xsl:call-template name="format-currency"><xsl:with-param name="amount" select="@value"/></xsl:call-template></fo:block></fo:table-cell>
				<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
			</fo:table-row>

		</xsl:template>

		<!-- Display the totals -->
		<xsl:template match="stmt:detail-totals">
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
				<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
				<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
				<fo:table-cell><fo:block text-align="end"><xsl:value-of select="@log-count"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block text-align="end"><xsl:call-template name="format-volume"><xsl:with-param name="volume" select="@volume"/></xsl:call-template></fo:block></fo:table-cell>
				<fo:table-cell><fo:block text-align="end"><xsl:call-template name="format-currency"><xsl:with-param name="amount" select="@value"/></xsl:call-template></fo:block></fo:table-cell>
				<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
			</fo:table-row>
		</xsl:template>

		<!-- Set up Section header and columns for Segregation Details Type Beachcomb -->
		<xsl:template match="stmt:segregation-details" mode="beachcomb">
			<fo:table table-layout="fixed" width="100%" border-collapse="collapse" table-omit-header-at-break="false" xsl:use-attribute-sets="indent">
				<fo:table-column column-width="proportional-column-width(1.1)"/> <!-- Beachcomb mark -->
				<fo:table-column column-width="proportional-column-width(1.1)"/> <!-- Species -->
				<fo:table-column column-width="proportional-column-width(1.4)"/> <!-- Product -->
				<fo:table-column column-width="proportional-column-width(0.5)"/> <!-- Grade -->
				<fo:table-column column-width="proportional-column-width(0.5)"/> <!-- NMV -->
				<fo:table-column column-width="proportional-column-width(0.5)"/> <!-- Pieces -->
				<fo:table-column column-width="proportional-column-width(0.9)"/>  <!-- Volume -->
				<fo:table-column column-width="proportional-column-width(0.8)"/>  <!-- Rate -->
				<fo:table-column column-width="proportional-column-width(0.8)"/>  <!-- Value -->

				<fo:table-header xsl:use-attribute-sets="column-header" text-align="start">

					<!-- Spacer -->
					<fo:table-row>
						<fo:table-cell number-columns-spanned="9"  xsl:use-attribute-sets="space-size">
							<fo:block> 
								&#160;
							</fo:block>
						</fo:table-cell>
					</fo:table-row>

					<!-- Section Header -->
					<fo:table-row>
						<fo:table-cell number-columns-spanned="9" >
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

					<!-- Column titles -->
					<fo:table-row>
						<fo:table-cell><fo:block end-indent="0.0in">Beachcomb Mark</fo:block></fo:table-cell>
						<fo:table-cell><fo:block end-indent="0.0in">Species</fo:block></fo:table-cell>
						<fo:table-cell><fo:block end-indent="0.0in">Product</fo:block></fo:table-cell>
						<fo:table-cell><fo:block end-indent="0.0in">Grade</fo:block></fo:table-cell>
						<fo:table-cell><fo:block end-indent="0.0in">NMV</fo:block></fo:table-cell>
						<fo:table-cell><fo:block start-indent="0.0in" text-align="end">Pieces</fo:block></fo:table-cell>
						<fo:table-cell><fo:block start-indent="0.0in" text-align="end">Volume (m3)</fo:block></fo:table-cell>
						<fo:table-cell><fo:block start-indent="0.0in" text-align="end">Rate ($/m3)</fo:block></fo:table-cell>
						<fo:table-cell><fo:block start-indent="0.0in" text-align="end">Value ($)</fo:block></fo:table-cell>
					</fo:table-row>

					<!-- Rule -->
					<fo:table-row line-height="0.5mm">
						<fo:table-cell number-columns-spanned="9"  xsl:use-attribute-sets="space-size">
							<fo:block display-align="before"> 
								<fo:leader xsl:use-attribute-sets="rule"/>
							</fo:block>
						</fo:table-cell>
					</fo:table-row>

					<!-- Spacer -->
					<fo:table-row>
						<fo:table-cell number-columns-spanned="9"  xsl:use-attribute-sets="space-size">
							<fo:block> 
								&#160;
							</fo:block>
						</fo:table-cell>
					</fo:table-row>
				</fo:table-header>

				<fo:table-body xsl:use-attribute-sets="indent regular-text">
					<!-- get the details and sort them accordingly -->
					<xsl:apply-templates select="stmt:segregation-detail" mode="beachcomb">
						<xsl:sort select="@beachcomb-mark"/>
						<xsl:sort select="@species"/>
						<xsl:sort select="@product"/>
						<xsl:sort select="@grade"/>
						<xsl:sort select="@mark-visible"/>
					</xsl:apply-templates>	
					<xsl:apply-templates select="stmt:segregation-totals" mode="beachcomb"/>
				</fo:table-body>
			</fo:table>
		</xsl:template>

		<!-- Segregation Details for Piece Scale BeachComb Invoice -->
		<xsl:template match="stmt:segregation-detail" mode="beachcomb">
			<fo:table-row>
				<fo:table-cell><fo:block end-indent="0.0in"><xsl:value-of select="@beachcomb-mark"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block end-indent="0.0in"><xsl:value-of select="@species"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block end-indent="0.0in"><xsl:value-of select="@product"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block end-indent="0.0in"><xsl:value-of select="@grade"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block end-indent="0.0in"><xsl:value-of select="@no-mark-visible-indicator"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block start-indent="0.0in" text-align="end"><xsl:value-of select="@pieces"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block start-indent="0.0in" text-align="end"><xsl:call-template name="format-volume"><xsl:with-param name="volume" select="@net-volume"/></xsl:call-template></fo:block></fo:table-cell>
				<fo:table-cell><fo:block start-indent="0.0in" text-align="end"><xsl:call-template name="format-currency"><xsl:with-param name="amount" select="@stumpage-rate"/></xsl:call-template></fo:block></fo:table-cell>
				<fo:table-cell><fo:block start-indent="0.0in" text-align="end"><xsl:call-template name="format-currency"><xsl:with-param name="amount" select="@value"/></xsl:call-template></fo:block></fo:table-cell>
			</fo:table-row>
		</xsl:template>

		<!-- Segregation Detail Totals for Piece Scale BeachComb Invoice-->
		<xsl:template match="stmt:segregation-totals" mode="beachcomb">
			<!-- Rule -->
			<fo:table-row>
				<fo:table-cell number-columns-spanned="9"  xsl:use-attribute-sets="space-size">
					<fo:block display-align="after"> 
						<fo:leader xsl:use-attribute-sets="rule"/>
					</fo:block>
				</fo:table-cell>
			</fo:table-row>

			<fo:table-row font-weight="bold">
				<fo:table-cell><fo:block>Segregations: <xsl:value-of select="count(../stmt:segregation-detail)"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
				<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
				<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
				<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
				<fo:table-cell><fo:block text-align="end"><xsl:value-of select="@pieces"/></fo:block></fo:table-cell>
				<fo:table-cell>
					<fo:block text-align="end">
						<xsl:call-template name="format-volume"><xsl:with-param name="volume" select="@volume"/></xsl:call-template>
					</fo:block>
				</fo:table-cell>
				<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
				<fo:table-cell>
					<fo:block text-align="end">
						<xsl:call-template name="format-currency">
							<xsl:with-param name="amount" select="@value"/>
						</xsl:call-template>
					</fo:block>
				</fo:table-cell>
				<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
			</fo:table-row>			
		</xsl:template>

		<!-- Set up Section header and columns for Segregation Details Type Cruise -->
		<xsl:template match="stmt:segregation-details" mode="cruise">
			<fo:table table-layout="fixed" width="100%" border-collapse="collapse" table-omit-header-at-break="false" xsl:use-attribute-sets="indent">
				<fo:table-column column-width="proportional-column-width(1.2)"/> 	<!-- Species -->
				<fo:table-column column-width="proportional-column-width(1.6)"/> 	<!-- Product -->
				<fo:table-column column-width="proportional-column-width(0.5)"/> 	<!-- Grade -->
				<fo:table-column column-width="proportional-column-width(1)"/> 		<!-- Volume -->
				<fo:table-column column-width="proportional-column-width(1)"/>   	<!-- Rate -->
				<fo:table-column column-width="proportional-column-width(1)"/>   	<!-- Value -->
				<fo:table-column column-width="proportional-column-width(1.5)"/>  <!-- left over -->

				<fo:table-header xsl:use-attribute-sets="column-header" text-align="start">

					<!-- Spacer -->
					<fo:table-row>
						<fo:table-cell number-columns-spanned="7"  xsl:use-attribute-sets="space-size">
							<fo:block> 
								&#160;
							</fo:block>
						</fo:table-cell>
					</fo:table-row>

					<!-- Section Header -->
					<fo:table-row>
						<fo:table-cell number-columns-spanned="7" >
							<fo:block xsl:use-attribute-sets="section-header-block section-header-text"> 
								Segregation Details
							</fo:block>
						</fo:table-cell>
					</fo:table-row>

					<!-- Rule -->
					<fo:table-row>
						<fo:table-cell number-columns-spanned="6"  xsl:use-attribute-sets="space-size">
							<fo:block display-align="after"> 
								<fo:leader xsl:use-attribute-sets="rule"/>
							</fo:block>
						</fo:table-cell>
					</fo:table-row>

					<!-- Column titles -->
					<fo:table-row>
						<fo:table-cell><fo:block>Species</fo:block></fo:table-cell>
						<fo:table-cell><fo:block>Product</fo:block></fo:table-cell>
						<fo:table-cell><fo:block>Grade</fo:block></fo:table-cell>
						<fo:table-cell><fo:block text-align="end">Volume (m3)</fo:block></fo:table-cell>
						<fo:table-cell><fo:block text-align="end">Rate ($/m3)</fo:block></fo:table-cell>
						<fo:table-cell><fo:block text-align="end">Value ($)</fo:block></fo:table-cell>
						<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
					</fo:table-row>

					<!-- Rule -->
					<fo:table-row line-height="0.5mm">
						<fo:table-cell number-columns-spanned="6"  xsl:use-attribute-sets="space-size">
							<fo:block display-align="before"> 
								<fo:leader xsl:use-attribute-sets="rule"/>
							</fo:block>
						</fo:table-cell>
					</fo:table-row>

					<!-- Spacer -->
					<fo:table-row>
						<fo:table-cell number-columns-spanned="7"  xsl:use-attribute-sets="space-size">
							<fo:block> 
								&#160;
							</fo:block>
						</fo:table-cell>
					</fo:table-row>
				</fo:table-header>
				<fo:table-body xsl:use-attribute-sets="indent regular-text">
					<!-- get the details and sort them accordingly -->
					<xsl:apply-templates select="stmt:segregation-detail" mode="cruise">
						<xsl:sort select="@species"/>
						<xsl:sort select="@product"/>
						<xsl:sort select="@grade"/>
					</xsl:apply-templates>	
					<xsl:apply-templates select="stmt:segregation-totals" mode="cruise"/>
				</fo:table-body>
			</fo:table>
		</xsl:template>

		<!-- Segregation Details for Piece Scale Cruise Invoice -->
		<xsl:template match="stmt:segregation-detail" mode="cruise">
			<fo:table-row>
				<fo:table-cell><fo:block><xsl:value-of select="@species"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block><xsl:value-of select="@product"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block><xsl:value-of select="@grade"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block text-align="end"><xsl:call-template name="format-volume"><xsl:with-param name="volume" select="@net-volume"/></xsl:call-template></fo:block></fo:table-cell>
				<fo:table-cell><fo:block text-align="end"><xsl:call-template name="format-currency"><xsl:with-param name="amount" select="@stumpage-rate"/></xsl:call-template></fo:block></fo:table-cell>
				<fo:table-cell><fo:block text-align="end"><xsl:call-template name="format-currency"><xsl:with-param name="amount" select="@value"/></xsl:call-template></fo:block></fo:table-cell>
				<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
			</fo:table-row>
		</xsl:template>

		<!-- Segregation Detail Totals for Piece Scale Cruise Invoice-->
		<xsl:template match="stmt:segregation-totals" mode="cruise">
			<!-- Rule -->
			<fo:table-row>
				<fo:table-cell number-columns-spanned="6"  xsl:use-attribute-sets="space-size">
					<fo:block display-align="after"> 
						<fo:leader xsl:use-attribute-sets="rule"/>
					</fo:block>
				</fo:table-cell>
			</fo:table-row>

			<fo:table-row font-weight="bold">
				<fo:table-cell><fo:block>Segregations: <xsl:value-of select="count(../stmt:segregation-detail)"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
				<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
				<fo:table-cell>
					<fo:block text-align="end">
						<xsl:call-template name="format-volume"><xsl:with-param name="volume" select="@volume"/></xsl:call-template>
					</fo:block>
				</fo:table-cell>
				<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
				<fo:table-cell>
					<fo:block text-align="end">
						<xsl:call-template name="format-currency">
							<xsl:with-param name="amount" select="@value"/>
						</xsl:call-template>
					</fo:block>
				</fo:table-cell>
				<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
			</fo:table-row>			
		</xsl:template>

		<!-- Set up Section header and columns for Segregation Details Type Field -->
		<xsl:template match="stmt:segregation-details" mode="field">
			<fo:table table-layout="fixed" width="100%" border-collapse="collapse" table-omit-header-at-break="false" xsl:use-attribute-sets="indent">
				<fo:table-column column-width="proportional-column-width(1.2)"/> 	<!-- Species -->
				<fo:table-column column-width="proportional-column-width(1.6)"/> 	<!-- Product -->
				<fo:table-column column-width="proportional-column-width(0.5)"/> 	<!-- Grade -->
				<fo:table-column column-width="proportional-column-width(0.7)"/> 	<!-- Pieces -->
				<fo:table-column column-width="proportional-column-width(1)"/> 		<!-- Volume -->
				<fo:table-column column-width="proportional-column-width(1)"/> 		<!-- Rate -->
				<fo:table-column column-width="proportional-column-width(1)"/> 		<!-- Value -->
				<fo:table-column column-width="proportional-column-width(1)"/> 		<!-- left over -->

				<fo:table-header xsl:use-attribute-sets="column-header" text-align="start">

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
								Segregation Details
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

					<!-- Column titles -->
					<fo:table-row>
						<fo:table-cell><fo:block end-indent="0.0in">Species</fo:block></fo:table-cell>
						<fo:table-cell><fo:block end-indent="0.0in">Product</fo:block></fo:table-cell>
						<fo:table-cell><fo:block end-indent="0.0in">Grade</fo:block></fo:table-cell>
						<fo:table-cell><fo:block start-indent="0.0in" text-align="end">Pieces</fo:block></fo:table-cell>
						<fo:table-cell><fo:block start-indent="0.0in" text-align="end">Volume (m3)</fo:block></fo:table-cell>
						<fo:table-cell><fo:block start-indent="0.0in" text-align="end">Rate ($/m3)</fo:block></fo:table-cell>
						<fo:table-cell><fo:block start-indent="0.0in" text-align="end">Value ($)</fo:block></fo:table-cell>
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
				<fo:table-body xsl:use-attribute-sets="indent regular-text">
					<!-- get the details and sort them accordingly -->
					<xsl:apply-templates select="stmt:segregation-detail" mode="field">
						<xsl:sort select="@species"/>
						<xsl:sort select="@product"/>
						<xsl:sort select="@grade"/>
					</xsl:apply-templates>	
					<xsl:apply-templates select="stmt:segregation-totals" mode="field"/>
				</fo:table-body>
			</fo:table>
		</xsl:template>

		<!-- Segregation Details for Piece Scale Field Invoice -->
		<xsl:template match="stmt:segregation-detail" mode="field">
			<fo:table-row>
				<fo:table-cell><fo:block end-indent="0.0in"><xsl:value-of select="@species"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block end-indent="0.0in"><xsl:value-of select="@product"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block end-indent="0.0in"><xsl:value-of select="@grade"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block start-indent="0.0in" text-align="end"><xsl:value-of select="@pieces"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block start-indent="0.0in" text-align="end"><xsl:call-template name="format-volume"><xsl:with-param name="volume" select="@net-volume"/></xsl:call-template></fo:block></fo:table-cell>
				<fo:table-cell><fo:block start-indent="0.0in" text-align="end"><xsl:call-template name="format-currency"><xsl:with-param name="amount" select="@stumpage-rate"/></xsl:call-template></fo:block></fo:table-cell>
				<fo:table-cell><fo:block start-indent="0.0in" text-align="end"><xsl:call-template name="format-currency"><xsl:with-param name="amount" select="@value"/></xsl:call-template></fo:block></fo:table-cell>
				<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
			</fo:table-row>
		</xsl:template>

		<!-- Segregation Detail Totals for Piece Scale Cruise Invoice-->
		<xsl:template match="stmt:segregation-totals" mode="field">
			<!-- Rule -->
			<fo:table-row>
				<fo:table-cell number-columns-spanned="7"  xsl:use-attribute-sets="space-size">
					<fo:block display-align="after"> 
						<fo:leader xsl:use-attribute-sets="rule"/>
					</fo:block>
				</fo:table-cell>
			</fo:table-row>

			<fo:table-row font-weight="bold">
				<fo:table-cell><fo:block>Segregations: <xsl:value-of select="count(../stmt:segregation-detail)"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
				<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
				<fo:table-cell><fo:block text-align="end"><xsl:value-of select="@pieces"/></fo:block></fo:table-cell>
				<fo:table-cell>
					<fo:block text-align="end">
						<xsl:call-template name="format-volume"><xsl:with-param name="volume" select="@volume"/></xsl:call-template>
					</fo:block>
				</fo:table-cell>
				<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
				<fo:table-cell>
					<fo:block text-align="end">
						<xsl:call-template name="format-currency">
							<xsl:with-param name="amount" select="@value"/>
						</xsl:call-template>
					</fo:block>
				</fo:table-cell>
				<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
			</fo:table-row>			
		</xsl:template>

		<!-- Set up Section header and columns for Segregation Details Type Primary -->
		<xsl:template match="stmt:segregation-details" mode="primary">
			<fo:table table-layout="fixed" width="100%" border-collapse="collapse" table-omit-header-at-break="false" xsl:use-attribute-sets="indent">
				<fo:table-column column-width="proportional-column-width(1.2)"/> <!-- Species -->
				<fo:table-column column-width="proportional-column-width(1.6)"/> <!-- Product -->
				<fo:table-column column-width="proportional-column-width(0.8)"/> <!-- Grade -->
				<fo:table-column column-width="proportional-column-width(0.5)"/> <!-- SB4 -->
				<fo:table-column column-width="proportional-column-width(0.5)"/> <!-- NMV -->
				<fo:table-column column-width="proportional-column-width(0.7)"/> <!-- Pieces -->
				<fo:table-column column-width="proportional-column-width(0.9)"/>   <!-- Volume -->
				<fo:table-column column-width="proportional-column-width(0.8)"/>   <!-- Rate -->
				<fo:table-column column-width="proportional-column-width(1)"/>   <!-- Value -->

				<fo:table-header xsl:use-attribute-sets="column-header" text-align="start">

					<!-- Spacer -->
					<fo:table-row>
						<fo:table-cell number-columns-spanned="9"  xsl:use-attribute-sets="space-size">
							<fo:block> 
								&#160;
							</fo:block>
						</fo:table-cell>
					</fo:table-row>

					<!-- Section Header -->
					<fo:table-row>
						<fo:table-cell number-columns-spanned="9" >
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

					<!-- Column titles -->
					<fo:table-row>
						<fo:table-cell><fo:block end-indent="0.0in">Species</fo:block></fo:table-cell>
						<fo:table-cell><fo:block end-indent="0.0in">Product</fo:block></fo:table-cell>
						<fo:table-cell><fo:block end-indent="0.0in">Grade</fo:block></fo:table-cell>
						<fo:table-cell><fo:block end-indent="0.0in">SB4</fo:block></fo:table-cell>
						<fo:table-cell><fo:block end-indent="0.0in">NMV</fo:block></fo:table-cell>
						<fo:table-cell><fo:block start-indent="0.0in" text-align="end">Pieces</fo:block></fo:table-cell>
						<fo:table-cell><fo:block start-indent="0.0in" text-align="end">Volume (m3)</fo:block></fo:table-cell>
						<fo:table-cell><fo:block start-indent="0.0in" text-align="end">Rate ($/m3)</fo:block></fo:table-cell>
						<fo:table-cell><fo:block start-indent="0.0in" text-align="end">Value ($)</fo:block></fo:table-cell>
						<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
					</fo:table-row>

					<!-- Rule -->
					<fo:table-row line-height="0.5mm">
						<fo:table-cell number-columns-spanned="9"  xsl:use-attribute-sets="space-size">
							<fo:block display-align="before"> 
								<fo:leader xsl:use-attribute-sets="rule"/>
							</fo:block>
						</fo:table-cell>
					</fo:table-row>

					<!-- Spacer -->
					<fo:table-row>
						<fo:table-cell number-columns-spanned="9"  xsl:use-attribute-sets="space-size">
							<fo:block> 
								&#160;
							</fo:block>
						</fo:table-cell>
					</fo:table-row>
				</fo:table-header>

				<fo:table-body xsl:use-attribute-sets="indent regular-text">
					<!-- get the details and sort them accordingly -->
					<xsl:apply-templates select="stmt:segregation-detail" mode="primary">
						<xsl:sort select="@species"/>
						<xsl:sort select="@product"/>
						<xsl:sort select="@grade"/>
						<xsl:sort select="@scaled-before"/>
						<xsl:sort select="@mark-visible"/>
					</xsl:apply-templates>	
					<xsl:apply-templates select="stmt:segregation-totals" mode="primary"/>
				</fo:table-body>
			</fo:table>
		</xsl:template>

		<!-- Segregation Details for Piece Scale Primary Invoice -->
		<xsl:template match="stmt:segregation-detail" mode="primary">
			<fo:table-row>
				<fo:table-cell><fo:block end-indent="0.0in"><xsl:value-of select="@species"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block end-indent="0.0in"><xsl:value-of select="@product"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block end-indent="0.0in"><xsl:value-of select="@grade"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block end-indent="0.0in"><xsl:value-of select="@scaled-before-indicator"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block end-indent="0.0in"><xsl:value-of select="@no-mark-visible-indicator"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block start-indent="0.0in" text-align="end"><xsl:value-of select="@pieces"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block start-indent="0.0in" text-align="end"><xsl:call-template name="format-volume"><xsl:with-param name="volume" select="@net-volume"/></xsl:call-template></fo:block></fo:table-cell>
				<fo:table-cell><fo:block start-indent="0.0in" text-align="end"><xsl:call-template name="format-currency"><xsl:with-param name="amount" select="@stumpage-rate"/></xsl:call-template></fo:block></fo:table-cell>
				<fo:table-cell><fo:block start-indent="0.0in" text-align="end"><xsl:call-template name="format-currency"><xsl:with-param name="amount" select="@value"/></xsl:call-template></fo:block></fo:table-cell>
				<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
			</fo:table-row>
		</xsl:template>

		<!-- Segregation Detail Totals for Piece Scale Primary Invoice -->
		<xsl:template match="stmt:segregation-totals" mode="primary">
			<!-- Rule -->
			<fo:table-row>
				<fo:table-cell number-columns-spanned="9"  xsl:use-attribute-sets="space-size">
					<fo:block display-align="after"> 
						<fo:leader xsl:use-attribute-sets="rule"/>
					</fo:block>
				</fo:table-cell>
			</fo:table-row>

			<fo:table-row font-weight="bold">
				<fo:table-cell><fo:block>Segregations: <xsl:value-of select="count(../stmt:segregation-detail)"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
				<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
				<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
				<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
				<fo:table-cell><fo:block text-align="end"><xsl:value-of select="@pieces"/></fo:block></fo:table-cell>
				<fo:table-cell>
					<fo:block text-align="end">
						<xsl:call-template name="format-volume"><xsl:with-param name="volume" select="@volume"/></xsl:call-template>
					</fo:block>
				</fo:table-cell>
				<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
				<fo:table-cell>
					<fo:block text-align="end">
						<xsl:call-template name="format-currency">
							<xsl:with-param name="amount" select="@value"/>
						</xsl:call-template>
					</fo:block>
				</fo:table-cell>
				<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
			</fo:table-row>			
		</xsl:template>

		<!-- Segregation Details for Piece Scale Waste Invoice -->
		<xsl:template match="stmt:segregation-details" mode="waste">
			<fo:table table-layout="fixed" width="100%" border-collapse="collapse" table-omit-header-at-break="false" xsl:use-attribute-sets="indent">
				<fo:table-column column-width="proportional-column-width(1.2)"/> <!-- Species -->
				<fo:table-column column-width="proportional-column-width(1.6)"/> <!-- Product -->
				<fo:table-column column-width="proportional-column-width(0.5)"/> <!-- Grade -->
				<fo:table-column column-width="proportional-column-width(0.7)"/> <!-- Avoidable -->
				<fo:table-column column-width="proportional-column-width(0.7)"/> <!-- Pieces -->
				<fo:table-column column-width="proportional-column-width(1)"/>   <!-- Volume -->
				<fo:table-column column-width="proportional-column-width(1)"/>   <!-- Rate -->
				<fo:table-column column-width="proportional-column-width(1)"/>   <!-- Value -->
				<fo:table-column column-width="proportional-column-width(0.3)"/> <!-- left over -->

				<fo:table-header xsl:use-attribute-sets="column-header" text-align="start">

					<!-- Spacer -->
					<fo:table-row>
						<fo:table-cell number-columns-spanned="9"  xsl:use-attribute-sets="space-size">
							<fo:block> 
								&#160;
							</fo:block>
						</fo:table-cell>
					</fo:table-row>

					<!-- Section Header -->
					<fo:table-row>
						<fo:table-cell number-columns-spanned="9" >
							<fo:block xsl:use-attribute-sets="section-header-block section-header-text"> 
								Segregation Details
							</fo:block>
						</fo:table-cell>
					</fo:table-row>

					<!-- Rule -->
					<fo:table-row>
						<fo:table-cell number-columns-spanned="8"  xsl:use-attribute-sets="space-size">
							<fo:block display-align="after"> 
								<fo:leader xsl:use-attribute-sets="rule"/>
							</fo:block>
						</fo:table-cell>
					</fo:table-row>

					<!-- Column titles -->
					<fo:table-row>
						<fo:table-cell><fo:block>Species</fo:block></fo:table-cell>
						<fo:table-cell><fo:block>Product</fo:block></fo:table-cell>
						<fo:table-cell><fo:block>Grade</fo:block></fo:table-cell>
						<fo:table-cell><fo:block text-align="center">Avoidable</fo:block></fo:table-cell>
						<fo:table-cell><fo:block text-align="end">Pieces</fo:block></fo:table-cell>
						<fo:table-cell><fo:block text-align="end">Volume (m3)</fo:block></fo:table-cell>
						<fo:table-cell><fo:block text-align="end">Rate ($/m3)</fo:block></fo:table-cell>
						<fo:table-cell><fo:block text-align="end">Value ($)</fo:block></fo:table-cell>
						<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
					</fo:table-row>

					<!-- Rule -->
					<fo:table-row line-height="0.5mm">
						<fo:table-cell number-columns-spanned="8"  xsl:use-attribute-sets="space-size">
							<fo:block display-align="before"> 
								<fo:leader xsl:use-attribute-sets="rule"/>
							</fo:block>
						</fo:table-cell>
					</fo:table-row>

					<!-- Spacer -->
					<fo:table-row>
						<fo:table-cell number-columns-spanned="9"  xsl:use-attribute-sets="space-size">
							<fo:block> 
								&#160;
							</fo:block>
						</fo:table-cell>
					</fo:table-row>
				</fo:table-header>

				<fo:table-body xsl:use-attribute-sets="indent regular-text">
					<!-- get the details and sort them accordingly -->
					<xsl:apply-templates select="stmt:segregation-detail" mode="waste">
						<xsl:sort select="@species"/>
						<xsl:sort select="@product"/>
						<xsl:sort select="@grade"/>
					</xsl:apply-templates>	
					<xsl:apply-templates select="stmt:segregation-totals" mode="waste"/>
				</fo:table-body>
			</fo:table>
		</xsl:template>

		<!-- Segregation Details for Piece Scale Waste Invoice -->
		<xsl:template match="stmt:segregation-detail" mode="waste">
			<fo:table-row>
				<fo:table-cell><fo:block><xsl:value-of select="@species"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block><xsl:value-of select="@product"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block><xsl:value-of select="@grade"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block text-align="center"><xsl:value-of select="@avoidable-indicator"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block text-align="end"><xsl:value-of select="@pieces"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block text-align="end"><xsl:call-template name="format-volume"><xsl:with-param name="volume" select="@net-volume"/></xsl:call-template></fo:block></fo:table-cell>
				<fo:table-cell><fo:block text-align="end"><xsl:call-template name="format-currency"><xsl:with-param name="amount" select="@stumpage-rate"/></xsl:call-template></fo:block></fo:table-cell>
				<fo:table-cell><fo:block text-align="end"><xsl:call-template name="format-currency"><xsl:with-param name="amount" select="@value"/></xsl:call-template></fo:block></fo:table-cell>
				<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
			</fo:table-row>
		</xsl:template>

		<!-- Segregation Detail Totals for Piece Scale Waste Invoice -->
		<xsl:template match="stmt:segregation-totals" mode="waste">
			<!-- Rule -->
			<fo:table-row>
				<fo:table-cell number-columns-spanned="8"  xsl:use-attribute-sets="space-size">
					<fo:block display-align="after"> 
						<fo:leader xsl:use-attribute-sets="rule"/>
					</fo:block>
				</fo:table-cell>
			</fo:table-row>

			<fo:table-row font-weight="bold">
				<fo:table-cell><fo:block>Segregations: <xsl:value-of select="count(../stmt:segregation-detail)"/></fo:block></fo:table-cell>
				<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
				<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
				<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
				<fo:table-cell><fo:block text-align="end"><xsl:value-of select="@pieces"/></fo:block></fo:table-cell>
				<fo:table-cell>
					<fo:block text-align="end">
						<xsl:call-template name="format-volume"><xsl:with-param name="volume" select="@volume"/></xsl:call-template>
					</fo:block>
				</fo:table-cell>
				<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
				<fo:table-cell>
					<fo:block text-align="end">
						<xsl:call-template name="format-currency">
							<xsl:with-param name="amount" select="@value"/>
						</xsl:call-template>
					</fo:block>
				</fo:table-cell>
				<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
			</fo:table-row>			
		</xsl:template>
	</xsl:stylesheet>
