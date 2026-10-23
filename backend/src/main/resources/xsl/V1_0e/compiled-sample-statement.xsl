<?xml version="1.0"?>
<xsl:stylesheet 
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
	xmlns:fo="http://www.w3.org/1999/XSL/Format"
	xmlns:stmt="gov.bc.ca/forests/hbs/statement/v1"
	exclude-result-prefixes="xsl"
	version="1.0"
	>
	<xsl:output method="xml" indent="yes"/>  

	<xsl:template match="stmt:compiled-sample-statements">
		<xsl:apply-templates/>
	</xsl:template>

	<xsl:template match="stmt:compiled-sample-statement">
		<fo:page-sequence master-reference="statement" initial-page-number="1" force-page-count="no-force">
			<xsl:comment>Page Border</xsl:comment> 
			<xsl:call-template name="page-border"/>

			<xsl:comment>Page Header</xsl:comment> 
			<xsl:call-template name="page-header">
				<xsl:with-param name="report-sub-type" select="'Compiled Sample'" />
				<xsl:with-param name="report-type" select="'Statement'" />
				<xsl:with-param name="report-number" select="stmt:header/stmt:statement-number" />
				<xsl:with-param name="end-of-report-id" select="generate-id(.)"/>
			</xsl:call-template>

			<xsl:comment>Footer</xsl:comment> 
			<xsl:call-template name="statement-footer">
				<xsl:with-param name="fine-print" select="stmt:fine-print" />
			</xsl:call-template> 

			<xsl:comment>MAIN DOCUMENT</xsl:comment> 
			<fo:flow flow-name="xsl-region-body">

				<xsl:apply-templates select="stmt:header" mode="css"/>

				<xsl:apply-templates select="stmt:detail-document" mode="css"/>
				<xsl:apply-templates select="stmt:segregation-details" mode="css"/>
				<xsl:apply-templates select="stmt:notations"/>
				<fo:block><xsl:attribute name="id"><xsl:value-of select="generate-id(.)"/></xsl:attribute></fo:block>
			</fo:flow>
		</fo:page-sequence>

	</xsl:template>

	<xsl:template match="stmt:header" mode="css">

		<xsl:call-template name="format-address">
			<xsl:with-param name="to" select="stmt:population-owner" />
			<xsl:with-param name="copy-to" select="stmt:stratum-owner" />
		</xsl:call-template>

		<fo:table table-layout="fixed" width="100%" border-collapse="collapse">
			<fo:table-column column-width="proportional-column-width(1)"/>
			<fo:table-column column-width="proportional-column-width(2)"/>
			<fo:table-column column-width="proportional-column-width(1)"/>
			<fo:table-column column-width="proportional-column-width(2)"/> 

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
					<fo:table-cell><fo:block font-weight="bold">Scale Date:</fo:block></fo:table-cell>
					<fo:table-cell>
						<fo:block>&#160;<xsl:call-template name="format-date"><xsl:with-param name="date" select="stmt:scale-date"/></xsl:call-template></fo:block>
					</fo:table-cell>
					<fo:table-cell><fo:block font-weight="bold">Transaction Type:</fo:block></fo:table-cell>
					<fo:table-cell><fo:block>&#160;<xsl:call-template name="format-tran-type"><xsl:with-param name="type" select="stmt:transaction-type"/></xsl:call-template></fo:block></fo:table-cell>

				</fo:table-row>

				<fo:table-row>
					<fo:table-cell><fo:block font-weight="bold">Scaler Licence:</fo:block></fo:table-cell>
					<fo:table-cell><fo:block>&#160;<xsl:value-of select="stmt:scaler/@license"/> - <xsl:value-of select="stmt:scaler/@name"/></fo:block></fo:table-cell>
					<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
					<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
				</fo:table-row>

				<fo:table-row>
					<fo:table-cell><fo:block font-weight="bold">Return Number:</fo:block></fo:table-cell>
					<fo:table-cell><fo:block>&#160;<xsl:value-of select="stmt:return-number"/></fo:block></fo:table-cell>
					<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
					<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
				</fo:table-row>

				<fo:table-row>
					<fo:table-cell><fo:block font-weight="bold">Timber Mark:</fo:block></fo:table-cell>
					<fo:table-cell><fo:block>&#160;<xsl:value-of select="stmt:timber-mark"/></fo:block></fo:table-cell>
					<fo:table-cell><fo:block font-weight="bold">Sample Weight:</fo:block></fo:table-cell>
					<fo:table-cell><fo:block>&#160;<xsl:call-template name="format-weight"><xsl:with-param name="weight" select="stmt:sample-weight"/></xsl:call-template>&#160;kg</fo:block></fo:table-cell>
				</fo:table-row>

				<fo:table-row>
					<fo:table-cell><fo:block font-weight="bold">Licence:</fo:block></fo:table-cell>
					<fo:table-cell><fo:block>&#160;<xsl:value-of select="stmt:license"/></fo:block></fo:table-cell>
					<fo:table-cell><fo:block font-weight="bold">Sample Volume:</fo:block></fo:table-cell>
					<fo:table-cell><fo:block>&#160;<xsl:call-template name="format-volume"><xsl:with-param name="volume" select="stmt:sample-volume"/></xsl:call-template>&#160;m3</fo:block></fo:table-cell>
				</fo:table-row>

				<fo:table-row>
					<fo:table-cell><fo:block font-weight="bold">Cutting Permit:</fo:block></fo:table-cell>
					<fo:table-cell><fo:block>&#160;<xsl:value-of select="stmt:cutting-permit"/></fo:block></fo:table-cell>
					<fo:table-cell><fo:block font-weight="bold">Weigh Slip:</fo:block></fo:table-cell>
					<fo:table-cell><fo:block>&#160;<xsl:value-of select="stmt:weigh-slip"/></fo:block></fo:table-cell>
				</fo:table-row>
			</fo:table-body>
		</fo:table>
	</xsl:template>

	<xsl:template match="stmt:segregation-details" mode="css">
		<fo:table table-layout="fixed" width="100%" border-collapse="collapse" table-omit-header-at-break="false" xsl:use-attribute-sets="indent">
			<fo:table-column column-width="proportional-column-width(1.2)"/> <!-- Species -->
			<fo:table-column column-width="proportional-column-width(1.0)"/> <!-- Grade -->
			<fo:table-column column-width="proportional-column-width(0.7)"/> <!-- Pieces -->
			<fo:table-column column-width="proportional-column-width(1.0)"/> <!-- Volume -->
			<fo:table-column column-width="proportional-column-width(1.0)"/> <!-- Fraction -->
			<fo:table-column column-width="proportional-column-width(0.9)"/> <!-- Ratio -->
			<fo:table-column column-width="proportional-column-width(1.6)"/> <!-- left over -->

			<fo:table-header xsl:use-attribute-sets="column-header" text-align="start">

				<fo:table-row>
					<fo:table-cell number-columns-spanned="7"  xsl:use-attribute-sets="space-size">
						<fo:block> 
							&#160;
						</fo:block>
					</fo:table-cell>
				</fo:table-row>

				<fo:table-row>
					<fo:table-cell number-columns-spanned="7" >
						<fo:block xsl:use-attribute-sets="section-header-block section-header-text"> 
							Segregation Details
						</fo:block>
					</fo:table-cell>
				</fo:table-row>

				<fo:table-row>
					<fo:table-cell number-columns-spanned="6"  xsl:use-attribute-sets="space-size">
						<fo:block display-align="after"> 
							<fo:leader xsl:use-attribute-sets="rule"/>
						</fo:block>
					</fo:table-cell>
				</fo:table-row>

				<fo:table-row>
					<fo:table-cell><fo:block end-indent="0.0in">&#160;<fo:block/>Species</fo:block></fo:table-cell>
					<fo:table-cell><fo:block end-indent="0.0in">&#160;<fo:block/>Grade</fo:block></fo:table-cell>
					<fo:table-cell><fo:block start-indent="0.0in" text-align="end">&#160;<fo:block/>Pieces</fo:block></fo:table-cell>
					<fo:table-cell><fo:block start-indent="0.0in" text-align="end">&#160;<fo:block/>Volume (m3)</fo:block></fo:table-cell>
					<fo:table-cell><fo:block start-indent="0.0in" text-align="end">&#160;<fo:block/>Fraction</fo:block></fo:table-cell>
					<fo:table-cell><fo:block start-indent="0.0in" text-align="end">Ratio (m3/tonne)</fo:block></fo:table-cell>
					<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
				</fo:table-row>

				<fo:table-row line-height="0.5mm">
					<fo:table-cell number-columns-spanned="6"  xsl:use-attribute-sets="space-size">
						<fo:block display-align="before"> 
							<fo:leader xsl:use-attribute-sets="rule"/>
						</fo:block>
					</fo:table-cell>
				</fo:table-row>

				<fo:table-row>
					<fo:table-cell number-columns-spanned="7"  xsl:use-attribute-sets="space-size">
						<fo:block> 
							&#160;
						</fo:block>
					</fo:table-cell>
				</fo:table-row>
			</fo:table-header>
			<fo:table-body xsl:use-attribute-sets="indent regular-text">
				<xsl:apply-templates select="stmt:segregation-detail" mode="css">
					<xsl:sort select="@species"/>
					<xsl:sort select="@grade"/>
				</xsl:apply-templates>	
				<xsl:apply-templates select="stmt:segregation-totals" mode="css"/>
			</fo:table-body>
		</fo:table>
	</xsl:template>

	<xsl:template match="stmt:segregation-detail" mode="css">
		<fo:table-row>
			<fo:table-cell><fo:block end-indent="0.0in"><xsl:value-of select="@species"/></fo:block></fo:table-cell>
			<fo:table-cell><fo:block end-indent="0.0in"><xsl:value-of select="@grade"/></fo:block></fo:table-cell>
			<fo:table-cell><fo:block start-indent="0.0in" text-align="end"><xsl:value-of select="@pieces"/></fo:block></fo:table-cell>
			<fo:table-cell><fo:block start-indent="0.0in" text-align="end"><xsl:call-template name="format-volume"><xsl:with-param name="volume" select="@volume"/></xsl:call-template></fo:block></fo:table-cell>
			<fo:table-cell><fo:block start-indent="0.0in" text-align="end"><xsl:value-of select="format-number(@fraction,'##0.00')"/>%</fo:block></fo:table-cell>
			<fo:table-cell><fo:block start-indent="0.0in" text-align="end"><xsl:call-template name="format-ratio"><xsl:with-param name="ratio" select="@ratio"/></xsl:call-template></fo:block></fo:table-cell>
			<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
		</fo:table-row>
	</xsl:template>

	<xsl:template match="stmt:segregation-totals" mode="css">
		<fo:table-row>
			<fo:table-cell number-columns-spanned="6"  xsl:use-attribute-sets="space-size">
				<fo:block display-align="after"> 
					<fo:leader xsl:use-attribute-sets="rule"/>
				</fo:block>
			</fo:table-cell>
		</fo:table-row>
		<fo:table-row font-weight="bold">
			<fo:table-cell><fo:block>Sample Totals: <xsl:value-of select="count(../stmt:segregation-detail)"/></fo:block></fo:table-cell>
			<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
			<fo:table-cell><fo:block start-indent="0.0in" text-align="end"><xsl:value-of select="@pieces"/></fo:block></fo:table-cell>
			<fo:table-cell><fo:block start-indent="0.0in" text-align="end"><xsl:call-template name="format-volume"><xsl:with-param name="volume" select="@volume"/></xsl:call-template></fo:block></fo:table-cell>
			<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
			<fo:table-cell><fo:block start-indent="0.0in" text-align="end"><xsl:call-template name="format-ratio"><xsl:with-param name="ratio" select="@ratio"/></xsl:call-template></fo:block></fo:table-cell>
		</fo:table-row>			
	</xsl:template>

	<xsl:template match="stmt:detail-document" mode="css">
		<fo:table table-layout="fixed" width="100%" border-collapse="collapse" table-omit-header-at-break="false" xsl:use-attribute-sets="indent regular-text">
			<fo:table-column column-width="proportional-column-width(1.5)"/>
			<fo:table-column column-width="proportional-column-width(2)"/>
			<fo:table-column column-width="proportional-column-width(1.5)"/>
			<fo:table-column column-width="proportional-column-width(2)"/> 

			<fo:table-header xsl:use-attribute-sets="column-header">

				<fo:table-row>
					<fo:table-cell number-columns-spanned="4"  xsl:use-attribute-sets="space-size">
						<fo:block> 
							&#160;
						</fo:block>
					</fo:table-cell>
				</fo:table-row>

				<fo:table-row>
					<fo:table-cell number-columns-spanned="4" >
						<fo:block xsl:use-attribute-sets="section-header-block section-header-text"> 
							Detail Document: <fo:inline xsl:use-attribute-sets="regular-text" font-weight="bold"><xsl:value-of select="@DDN"/></fo:inline>
						</fo:block>
					</fo:table-cell>
				</fo:table-row>

			</fo:table-header>
			<fo:table-body xsl:use-attribute-sets="regular-text indent">

				<fo:table-row>
					<fo:table-cell><fo:block font-weight="bold" end-indent="0.0in">Load Description Slip:</fo:block></fo:table-cell>
					<fo:table-cell><fo:block>&#160;<xsl:value-of select="@LDS-number"/></fo:block></fo:table-cell>
					<fo:table-cell><fo:block font-weight="bold">Tare Weight:</fo:block></fo:table-cell>
					<fo:table-cell>
						<fo:block>
							<xsl:choose>
								<xsl:when test="@tare-weight">
									&#160;<xsl:call-template name="format-weight"><xsl:with-param name="weight" select="@tare-weight"/></xsl:call-template>&#160;kg
								</xsl:when>
								<xsl:otherwise>
									&#160;N/A
								</xsl:otherwise>
							</xsl:choose>
						</fo:block>
					</fo:table-cell>
				</fo:table-row>

				<fo:table-row>
					<xsl:choose>
						<xsl:when test="@original-DDN">
							<fo:table-cell><fo:block font-weight="bold">Original Document:</fo:block></fo:table-cell>
							<fo:table-cell><fo:block>&#160;<xsl:value-of select="@original-DDN"/></fo:block></fo:table-cell>
						</xsl:when>
						<xsl:otherwise>
							<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
							<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
						</xsl:otherwise>
					</xsl:choose>						

					<fo:table-cell><fo:block font-weight="bold">Grapple Weight:</fo:block></fo:table-cell>
					<fo:table-cell>
						<fo:block>
							<xsl:choose>
								<xsl:when test="@grapple-weight">
									&#160;<xsl:call-template name="format-weight"><xsl:with-param name="weight" select="@grapple-weight"/></xsl:call-template>&#160;kg
								</xsl:when>
								<xsl:otherwise>
									&#160;N/A
								</xsl:otherwise>
							</xsl:choose>
						</fo:block>
					</fo:table-cell>
				</fo:table-row>

				<fo:table-row>
					<xsl:choose>
						<xsl:when test="@original-scaler-license">
							<fo:table-cell><fo:block font-weight="bold">Original Scaler:</fo:block></fo:table-cell>
							<fo:table-cell><fo:block>&#160;<xsl:value-of select="@original-scaler-license"/> - <xsl:value-of select="@original-scaler-name"/></fo:block></fo:table-cell>
						</xsl:when>
						<xsl:otherwise>
							<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
							<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
						</xsl:otherwise>
					</xsl:choose>											
					<fo:table-cell><fo:block font-weight="bold">Gross Weight:</fo:block></fo:table-cell>
					<fo:table-cell>
						<fo:block>
							<xsl:choose>
								<xsl:when test="@gross-weight">
									&#160;<xsl:call-template name="format-weight"><xsl:with-param name="weight" select="@gross-weight"/></xsl:call-template>&#160;kg
								</xsl:when>
								<xsl:otherwise>
									&#160;N/A
								</xsl:otherwise>
							</xsl:choose>
						</fo:block>
					</fo:table-cell>
				</fo:table-row>

				<xsl:if test="@original-return-number">
					<fo:table-row>
						<fo:table-cell><fo:block font-weight="bold">Original Return:</fo:block></fo:table-cell>
						<fo:table-cell><fo:block>&#160;<xsl:value-of select="@original-return-number"/></fo:block></fo:table-cell>
						<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
						<fo:table-cell><fo:block>&#160;</fo:block></fo:table-cell>
					</fo:table-row>
				</xsl:if>

			</fo:table-body>
		</fo:table>
	</xsl:template>
</xsl:stylesheet>
