<?xml version="1.0"?>
<!--
  $RCSfile: xslt-functions.xsl,v $ 
  Copyright (c) 2001, B.C. Ministry of Forests. 
  All rights reserved. 

	Author:  Chris McCann
	Created: 2002-02-27
	Version: $Revision: 1.4 $ $Author: CWMcCann $ $Date: 2003/10/27 22:21:16 $

	Functions for doing common formating
	-->
	<xsl:stylesheet 
		xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
		xmlns:fo="http://www.w3.org/1999/XSL/Format" 
		xmlns:stmt="gov.bc.ca/forests/hbs/statement/v1" 
		version="1.0">
		<xsl:output method="xml" indent="yes"/>
		<xsl:variable name="upper-case" select="'ABCDEFGHIJKLMNPOQRSTUVWXYZ'"/>
		<xsl:variable name="lower-case" select="'abcdefghijklmnpoqrstuvwxyz'"/>

			<!-- 
		Given a Transaction Type appreviation print out the whole word 
		@param type The type to expand can be 'REP', 'ORG', 'CAN', 'RWC', 'RWN' 
		-->
		<xsl:template name="format-tran-type">
			<xsl:param name="type" />
			<xsl:choose>
				<xsl:when test="$type='REP'">Replacement</xsl:when>
				<xsl:when test="$type='ORG'">Original</xsl:when>
				<xsl:when test="$type='CAN'">Cancel</xsl:when>
				<xsl:when test="$type='RWC'">Replace with changes</xsl:when>
				<xsl:when test="$type='RWN'">Replace with no changes</xsl:when>
			</xsl:choose>
		</xsl:template>

		<!-- 
		Given a Transaction Type appreviation print out the whole word 
		@param type The type to expand can be 'REP', 'ORG', 'CAN', 'RWC', 'RWN' (special version for ratio to conserve space)
		-->
		<xsl:template name="format-tran-type-ratio">
			<xsl:param name="type" />
			<xsl:choose>
				<xsl:when test="$type='REP'">Replace</xsl:when>
				<xsl:when test="$type='ORG'">Original</xsl:when>
				<xsl:when test="$type='CAN'">Cancel</xsl:when>
				<xsl:when test="$type='RWC'">Replace</xsl:when>
				<xsl:when test="$type='RWN'">Replace</xsl:when>
			</xsl:choose>
		</xsl:template>

		<!-- 
		Format an xml schema date type from yyyy-mm-dd to yyyy-mmm-dd
		@param date the date to format must be yyyy-mm-dd
		-->
		<xsl:template name="format-date">
			<xsl:param name="date" />

			<!-- Year -->
			<xsl:value-of select="substring($date, 0, 5)" />
			<xsl:text>-</xsl:text>		

			<!-- Month -->
			<xsl:variable name="month" 
				select="number(substring($date, 6, 2))"/>
			<xsl:choose>
				<xsl:when test="$month=1">Jan</xsl:when>
				<xsl:when test="$month=2">Feb</xsl:when>
				<xsl:when test="$month=3">Mar</xsl:when>
				<xsl:when test="$month=4">Apr</xsl:when>
				<xsl:when test="$month=5">May</xsl:when>
				<xsl:when test="$month=6">Jun</xsl:when>
				<xsl:when test="$month=7">Jul</xsl:when>
				<xsl:when test="$month=8">Aug</xsl:when>
				<xsl:when test="$month=9">Sep</xsl:when>
				<xsl:when test="$month=10">Oct</xsl:when>
				<xsl:when test="$month=11">Nov</xsl:when>
				<xsl:when test="$month=12">Dec</xsl:when>
				<xsl:otherwise></xsl:otherwise>
			</xsl:choose>
			<xsl:text>-</xsl:text>

			<!-- Day -->
			<xsl:value-of select="substring($date, 9, 2)" />
		</xsl:template>

		<!-- 
		Given a Transaction Type appreviation print out the whole word 
		@param type The type to expand can be 'REP', 'ORG', or 'CAN' 
		-->
		<xsl:template name="format-stmt-type">
			<xsl:param name="type" />
			<xsl:choose>
				<xsl:when test="$type='PSI'">Piece Scale Invoice</xsl:when>
				<xsl:when test="$type='PSV'">Piece Scale Volume Statement</xsl:when>
				<xsl:when test="$type='WSI'">Weight Scale Invoice</xsl:when>
				<xsl:when test="$type='WSV'">Weight Scale Volume Statement</xsl:when>
				<xsl:when test="$type='CSS'">Compiled Sample Statement</xsl:when>
				<xsl:when test="$type='RS'">Ratio Statement</xsl:when>
			</xsl:choose>
		</xsl:template>
		
		<!-- Format the address to a client in a 4 column table -->
		<xsl:template name="format-address">
			<xsl:param name="to"/>
			<xsl:param name="copy-to"/>

			<fo:block-container height="1.25in" width="3.7in" position="absolute" left=".25in" top="0.80in">
				<fo:block start-indent="0.0in" end-indent="0.5in" xsl:use-attribute-sets="address-border address-text" >
					<xsl:call-template name="print-address">
						<xsl:with-param name="address" select="$to"/>
					</xsl:call-template>
				</fo:block>
			</fo:block-container>

			<fo:block-container height="1.25in" width="3.7in" position="absolute" left="4.0in" top="0.80in">
				<fo:block start-indent="0.0in" end-indent="0.5in" xsl:use-attribute-sets="address-border address-text">
					<xsl:call-template name="print-address">
						<xsl:with-param name="address" select="$copy-to"/>
					</xsl:call-template>
				</fo:block>
			</fo:block-container>
		</xsl:template>

		<!-- 
		Format a volume amount to include commas and 3 decimal places 
		@param volume the number to format
		-->
		<xsl:template name="format-volume">
			<xsl:param name="volume"/>
			<xsl:value-of select="format-number($volume, '###,##0.000')"/>
		</xsl:template>

		<!-- 
		Format a weight amount to include commas
		@param weight the number to format
		-->
		<xsl:template name="format-weight">
			<xsl:param name="weight"/>
			<xsl:value-of select="format-number($weight, '###,###,##0')"/>
		</xsl:template>

		<!-- 
		Format a weightxrate amount to include commas
		@param weightxrate the number to format
		-->
		<xsl:template name="format-weight-x-rate">
			<xsl:param name="weightxrate"/>
			<xsl:value-of select="format-number($weightxrate, '###,###,###,###,##0.00')"/>
		</xsl:template>

		<!-- 
		Format a ratio amount to include 5 decimal places
		@param ratio the number to format
		-->
		<xsl:template name="format-ratio">
			<xsl:param name="ratio"/>
			<xsl:value-of select="format-number($ratio, '##0.00000')"/>
		</xsl:template>

		<!-- 
		Format a currency amount to include commas and 2 decimal places
		@param amount the number to format
		-->
		<xsl:template name="format-currency">
			<xsl:param name="amount"/>
			<xsl:value-of select="format-number($amount, '#,###,##0.00')"/>
		</xsl:template>

		<!-- Formats the recipeint Type -->
		<xsl:template name="format-recip-type">
			<xsl:param name="type" />
			<xsl:choose>
				<xsl:when test="$type='SEND_TO'">Send To</xsl:when>
				<xsl:when test="$type='COPY_TO'">Copy To</xsl:when>
			</xsl:choose>
		</xsl:template>

		<!-- 
		simple template to make it easy to change the position of the page border
		since it is dependant on the page layout. -->
		<xsl:template name="page-border">
			<fo:static-content flow-name="xsl-region-start">
				<fo:block-container xsl:use-attribute-sets="page-border" top="-0.9in" left="0in" position="absolute" width="8.0in" height="10.5in">
				</fo:block-container> 
			</fo:static-content>
		</xsl:template>

		<xsl:template name="print-address">
			<xsl:param name="address"/>
			
			&#160;&#160;<xsl:value-of select="translate($address/stmt:name, $lower-case, $upper-case)"/><fo:block/>
			<xsl:if test="$address/stmt:location-name">
				&#160;&#160;<xsl:value-of select="translate($address/stmt:location-name, $lower-case, $upper-case)"/><fo:block/>
			</xsl:if>
			<xsl:if test="$address/stmt:address1">
				&#160;&#160;<xsl:value-of select="translate($address/stmt:address1, $lower-case, $upper-case)"/><fo:block/>
			</xsl:if>
			<xsl:if test="$address/stmt:address2">
				&#160;&#160;<xsl:value-of select="translate($address/stmt:address2, $lower-case, $upper-case)"/><fo:block/>
			</xsl:if>
			<xsl:if test="$address/stmt:address3">
				&#160;&#160;<xsl:value-of select="translate($address/stmt:address3, $lower-case, $upper-case)"/><fo:block/>
			</xsl:if>
			<xsl:choose>
				<xsl:when	test="not($address)">
					&#160;<fo:block/>
				</xsl:when>
				<xsl:when	test="contains($address/stmt:name, 'NOT RELEASABLE')">
					&#160;<fo:block/>
				</xsl:when>
				<xsl:otherwise>
					&#160;&#160;<xsl:value-of select="translate($address/stmt:city, $lower-case, $upper-case)"/><xsl:text>, </xsl:text><xsl:value-of select="translate($address/stmt:province, $lower-case, $upper-case)"/>&#160;&#160;<xsl:value-of select="translate($address/stmt:postal-code, $lower-case, $upper-case)"/><fo:block/>
				</xsl:otherwise>
			</xsl:choose>
			<!-- Only print the country if is is not canada -->
			<xsl:if test="$address/stmt:country">
				<xsl:if test="not((translate($address/stmt:country, $lower-case, $upper-case)) = 'CANADA')">
					&#160;&#160;<xsl:value-of select="translate($address/stmt:country, $lower-case, $upper-case)"/><fo:block/>
			</xsl:if>
			</xsl:if>
		</xsl:template>

	</xsl:stylesheet>
