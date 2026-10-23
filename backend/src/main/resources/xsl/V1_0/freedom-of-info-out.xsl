<?xml version="1.0"?>
<!--
  $RCSfile: freedom-of-info-out.xsl,v $ 
  Copyright (c) 2001, B.C. Ministry of Forests. 
  All rights reserved. 

	Author:  Chris McCann
	Created: 2002-02-27
	Version: $Revision: 1.1 $ $Author: irkinnon $ $Date: 2004/09/03 22:33:36 $

	-->
	<xsl:stylesheet 
		xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
		xmlns:fo="http://www.w3.org/1999/XSL/Format" 
		xmlns:stmt="gov.bc.ca/forests/hbs/statement/v1"
		xmlns="gov.bc.ca/forests/hbs/statement/v1"
		version="1.0">
		<xsl:output method="xml" indent="yes"/>

		<xsl:template match="/ | @* | node()">
			<xsl:copy>
				<xsl:apply-templates select="@* | node()"/>
			</xsl:copy>
		</xsl:template>

		<xsl:template match="stmt:invoice-to | stmt:copy-to | stmt:population-owner | stmt:stratum-owner">
			<xsl:variable name="node-name" select="name()" /> 

			<xsl:element name="{$node-name}">
				<client-number><xsl:value-of select="stmt:client-number"/></client-number>
				<client-code><xsl:value-of select="stmt:client-code"/></client-code>
				<name>NOT RELEASABLE</name>
				<address1>*****</address1>
				<address2>*****</address2>
				<address3>*****</address3>
				<city>*****</city>
				<province>*****</province>
				<country>*****</country>
				<postal-code>*****</postal-code>
			</xsl:element>

		</xsl:template>

	</xsl:stylesheet>
