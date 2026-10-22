<?xml version="1.0"?>
<!--
  $RCSfile: piece-scale-volume-statement.xsl,v $ 
  Copyright (c) 2001, B.C. Ministry of Forests. 
  All rights reserved. 
  
  Author:  Chris McCann
  Created: 2002-02-27
	Version: $Revision: 1.3 $ $Author: CWMcCann $ $Date: 2003/10/09 22:28:11 $

	Piece Scale Volume Statement StyleSheet
-->
<xsl:stylesheet 
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
	xmlns:fo="http://www.w3.org/1999/XSL/Format" 
	xmlns:stmt="gov.bc.ca/forests/hbs/statement/v1"
	version="1.0">
	<xsl:output method="xml" indent="yes"/>  

	<!-- match each batch of volume statements -->
	<xsl:template match="stmt:piece-scale-volume-statements">
		<xsl:apply-templates/>
	</xsl:template>

	<!-- for each statement set up a page sequence -->
	<xsl:template match="stmt:piece-scale-volume-statement">
		<fo:page-sequence master-reference="statement" initial-page-number="1" force-page-count="no-force">
			<!-- Create the Page Border -->
			<xsl:call-template name="page-border"/>	

			<!-- Create the Page Header -->
			<xsl:call-template name="page-header">
				<xsl:with-param name="report-sub-type" select="'Piece Scale'" />
				<xsl:with-param name="report-type" select="'Volume Statement'" />
				<xsl:with-param name="report-number" select="stmt:header/stmt:statement-number" />
				<xsl:with-param name="end-of-report-id" select="generate-id(.)"/>
			</xsl:call-template>

			<!-- Create the Page Footer -->
			<xsl:call-template name="statement-footer">
				<xsl:with-param name="fine-print" select="stmt:fine-print" />
			</xsl:call-template> 


			<xsl:comment>MAIN DOCUMENT</xsl:comment> 
			<fo:flow flow-name="xsl-region-body">

				<xsl:apply-templates select="stmt:header" mode="pvs"/>

				<xsl:apply-templates select="stmt:segregation-details" mode="psv"/>
				<xsl:apply-templates select="stmt:detail-documents" mode="psv"/>
				<xsl:apply-templates select="stmt:notations"/>
				
				<!-- used for proper page numbering.-->
				<fo:block><xsl:attribute name="id"><xsl:value-of select="generate-id(.)"/></xsl:attribute></fo:block>

			</fo:flow>
		</fo:page-sequence>
	</xsl:template>

	<!-- Piece Scale Volume Statement Header -->
	<xsl:template match="stmt:header" mode="pvs">

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

			<!-- Header Section text -->
			<fo:table-header xsl:use-attribute-sets="section-header-text">
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
					<fo:table-cell><fo:block font-weight="bold">Region Harvested:</fo:block></fo:table-cell>
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
							&#160;<xsl:if test="stmt:scale-period/@start"> <xsl:call-template name="format-date"><xsl:with-param name="date" select="stmt:scale-period/@start"/></xsl:call-template> to </xsl:if><xsl:call-template name="format-date"><xsl:with-param name="date" select="stmt:scale-period/@end"/></xsl:call-template>
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
						<fo:block>&#160;<xsl:if test="stmt:scale-site"><xsl:value-of select="stmt:scale-site/@id"/> - <xsl:value-of select="stmt:scale-site/@name"/></xsl:if></fo:block>
					</fo:table-cell>
					<fo:table-cell><fo:block font-weight="bold">Return Category:</fo:block></fo:table-cell>
					<fo:table-cell><fo:block>&#160;<xsl:value-of select="stmt:return-category/@description"/></fo:block></fo:table-cell>
				</fo:table-row>

				<fo:table-row>
					<fo:table-cell><fo:block font-weight="bold">Timber Mark:</fo:block></fo:table-cell>

						<!-- if the deck id is present display it-->
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

									<xsl:otherwise>
										&#160;<xsl:value-of select="stmt:timber-mark"/>
									</xsl:otherwise>

								</xsl:choose>
							</fo:block>
						</fo:table-cell>
					<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
					<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
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
					<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
					<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
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
						<fo:block>&#160;</fo:block>
					</fo:table-cell>
					<fo:table-cell>
						<fo:block>&#160;</fo:block>
					</fo:table-cell>

					<fo:table-cell>
						<fo:block>Total Volume:</fo:block>
					</fo:table-cell>
					<fo:table-cell>
						<fo:block text-align="end">&#160;<xsl:call-template name="format-volume"><xsl:with-param name="volume" select="stmt:total-volume"/></xsl:call-template>&#160;m3</fo:block>
					</fo:table-cell>
				</fo:table-row>
			</fo:table-body>
		</fo:table>
	</xsl:template>

	<!-- Create the segregation section header and table columns -->
	<xsl:template match="stmt:segregation-details" mode="psv">
		<fo:table table-layout="fixed" width="100%" border-collapse="collapse" table-omit-header-at-break="false" xsl:use-attribute-sets="indent">
			<fo:table-column column-width="proportional-column-width(1.2)"/> <!-- Species -->
			<fo:table-column column-width="proportional-column-width(1.6)"/> <!-- Product -->
			<fo:table-column column-width="proportional-column-width(1.5)"/> <!-- Grade -->
			<fo:table-column column-width="proportional-column-width(0.7)"/> <!-- Pieces -->
			<fo:table-column column-width="proportional-column-width(1)"/>  <!-- Volume -->
			<fo:table-column column-width="proportional-column-width(2)"/>  <!-- left over -->

			<fo:table-header xsl:use-attribute-sets="column-header" text-align="start">

				<!-- Spacer -->
				<fo:table-row>
					<fo:table-cell number-columns-spanned="6"  xsl:use-attribute-sets="space-size">
						<fo:block> 
							&#160;
						</fo:block>
					</fo:table-cell>
				</fo:table-row>

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

				<!-- Tabler headers -->
				<fo:table-row>
					<fo:table-cell><fo:block end-indent="0.0in">Species</fo:block></fo:table-cell>
					<fo:table-cell><fo:block end-indent="0.0in">Product</fo:block></fo:table-cell>
					<fo:table-cell><fo:block end-indent="0.0in">Grade</fo:block></fo:table-cell>
					<fo:table-cell><fo:block start-indent="0.0in" text-align="end">Pieces</fo:block></fo:table-cell>
					<fo:table-cell><fo:block start-indent="0.0in" text-align="end">Volume (m3)</fo:block></fo:table-cell>
					<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
				</fo:table-row>

				<!-- Rule -->
				<fo:table-row line-height="0.5mm">
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
				<!-- Sort the order in which the details are displayed -->
				<xsl:apply-templates select="stmt:segregation-detail" mode="psv">
					<xsl:sort select="@species"/>
					<xsl:sort select="@product"/>
					<xsl:sort select="@grade"/>
				</xsl:apply-templates>
				<!-- Now print the totals -->	
				<xsl:apply-templates select="stmt:segregation-totals" mode="psv"/>
			</fo:table-body>
		</fo:table>
	</xsl:template>

	<!-- Display the detail items -->
	<xsl:template match="stmt:segregation-detail" mode="psv">
		<fo:table-row>
			<fo:table-cell><fo:block end-indent="0.0in"><xsl:value-of select="@species"/></fo:block></fo:table-cell>
			<fo:table-cell><fo:block end-indent="0.0in"><xsl:value-of select="@product"/></fo:block></fo:table-cell>
			<fo:table-cell><fo:block end-indent="0.0in"><xsl:value-of select="@grade"/></fo:block></fo:table-cell>
			<fo:table-cell><fo:block start-indent="0.0in" text-align="end"><xsl:value-of select="@pieces"/></fo:block></fo:table-cell>
			<fo:table-cell><fo:block start-indent="0.0in" text-align="end"><xsl:call-template name="format-volume"><xsl:with-param name="volume" select="@net-volume"/></xsl:call-template></fo:block></fo:table-cell>
			<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
		</fo:table-row>
	</xsl:template>

	<!-- Display the totals -->
	<xsl:template match="stmt:segregation-totals" mode="psv">
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
			<fo:table-cell><fo:block text-align="end"><xsl:value-of select="@pieces"/></fo:block></fo:table-cell>
			<fo:table-cell>
				<fo:block text-align="end">
					<xsl:call-template name="format-volume"><xsl:with-param name="volume" select="@volume"/></xsl:call-template>
				</fo:block>
			</fo:table-cell>
			<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
		</fo:table-row>			
	</xsl:template>

	<!-- Set up the Section and column headers for details -->
	<xsl:template match="stmt:detail-documents" mode="psv">
		<fo:table table-layout="fixed" width="100%" border-collapse="collapse" table-omit-header-at-break="false" xsl:use-attribute-sets="indent regular-text">
			<fo:table-column column-width="proportional-column-width(1.3)"/> <!-- Document # -->
			<fo:table-column column-width="proportional-column-width(0.8)"/> <!-- Scale Date -->
			<fo:table-column column-width="proportional-column-width(0.6)"/> <!-- Scaler Licence -->
			<fo:table-column column-width="proportional-column-width(0.6)"/> <!-- Return Number -->
			<fo:table-column column-width="proportional-column-width(1.0)"/> <!-- Load Arrival -->
			<fo:table-column column-width="proportional-column-width(0.9)"/> <!-- LDS -->
			<fo:table-column column-width="proportional-column-width(0.5)"/> <!-- Log Count -->
			<fo:table-column column-width="proportional-column-width(0.9)"/> <!-- Volume -->
			<fo:table-column column-width="proportional-column-width(1.4)"/> <!-- left over -->

			<fo:table-header xsl:use-attribute-sets="column-header">
				<!-- Spacer -->
				<fo:table-row>
					<fo:table-cell number-columns-spanned="9"  xsl:use-attribute-sets="space-size">
						<fo:block> 
							&#160;
						</fo:block>
					</fo:table-cell>`
				</fo:table-row>

				<!-- Section Header -->
				<fo:table-row>
					<fo:table-cell number-columns-spanned="9" >
						<fo:block xsl:use-attribute-sets="section-header-block section-header-text"> 
							Document Details
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

				<!-- Column headers -->
				<fo:table-row>
					<fo:table-cell><fo:block>Document Number</fo:block></fo:table-cell>
					<fo:table-cell><fo:block>Scale Date</fo:block></fo:table-cell>
					<fo:table-cell><fo:block>Scaler Licence</fo:block></fo:table-cell>
					<fo:table-cell><fo:block>Return Number</fo:block></fo:table-cell>
					<fo:table-cell><fo:block>Load Arrival Number</fo:block></fo:table-cell>
					<fo:table-cell><fo:block>LDS Number</fo:block></fo:table-cell>
					<fo:table-cell><fo:block text-align="end">Log Count</fo:block></fo:table-cell>
					<fo:table-cell><fo:block text-align="end">Volume (m3)</fo:block></fo:table-cell>
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
			<fo:table-body xsl:use-attribute-sets="regular-text indent">
				<!-- Sort all the details and apply the template -->
				<xsl:apply-templates select="stmt:detail-document" mode="psv">
					<xsl:sort select="@scale-date"/>
					<xsl:sort select="@scaler-license"/>
					<xsl:sort select="@return-number"/>
				</xsl:apply-templates>
				<xsl:apply-templates select="stmt:detail-totals" mode="psv"/>
			</fo:table-body>
		</fo:table>
	</xsl:template>

	<!-- Display the detail items -->
	<xsl:template match="stmt:detail-document" mode="psv">
		<fo:table-row>
			<fo:table-cell><fo:block text-align="start"><xsl:value-of select="@DDN"/></fo:block></fo:table-cell>
			<fo:table-cell>
				<fo:block>
					<xsl:call-template name="format-date"><xsl:with-param name="date" select="@scale-date" /></xsl:call-template>
				</fo:block>
			</fo:table-cell>
			<fo:table-cell><fo:block><xsl:value-of select="@scaler-license"/></fo:block></fo:table-cell>
			<fo:table-cell><fo:block><xsl:value-of select="@return-number"/></fo:block></fo:table-cell>
			<fo:table-cell><fo:block><xsl:value-of select="@load-arrival-number"/></fo:block></fo:table-cell>
			<fo:table-cell><fo:block><xsl:value-of select="@LDS-number"/></fo:block></fo:table-cell>
			<fo:table-cell><fo:block text-align="end"><xsl:value-of select="@log-count"/></fo:block></fo:table-cell>
			<fo:table-cell><fo:block text-align="end"><xsl:call-template name="format-volume"><xsl:with-param name="volume" select="@volume"/></xsl:call-template></fo:block></fo:table-cell>
			<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
		</fo:table-row>

	</xsl:template>
	
	<!-- Display the detail totals -->
	<xsl:template match="stmt:detail-totals" mode="psv">
		<!-- Rule -->
		<fo:table-row>
			<fo:table-cell number-columns-spanned="8"  xsl:use-attribute-sets="space-size">
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
			<fo:table-cell><fo:block text-align="end"><xsl:value-of select="stmt:log-count"/></fo:block></fo:table-cell>
			<fo:table-cell><fo:block text-align="end"><xsl:call-template name="format-volume"><xsl:with-param name="volume" select="@volume"/></xsl:call-template></fo:block></fo:table-cell>
			<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
		</fo:table-row>
	</xsl:template>
</xsl:stylesheet>
