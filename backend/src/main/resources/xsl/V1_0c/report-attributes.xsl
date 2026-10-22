<?xml version="1.0"?>
<!--
  $RCSfile: report-attributes.xsl,v $ 
  Copyright (c) 2001, B.C. Ministry of Forests. 
  All rights reserved. 

	Author:  Chris McCann
	Created: 2002-02-27
	Version: $Revision: 1.1 $ $Author: CWMcCann $ $Date: 2003/08/12 19:58:50 $

	Font / Size attributes that are global to all the invoices and statements
	-->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:fo="http://www.w3.org/1999/XSL/Format" version="1.0">
	<xsl:output method="xml" indent="yes"/>

	<xsl:variable name="line-thickness" select="'0.1mm'"/>
	<xsl:variable name="line-color" select="'black'"/>
	<xsl:variable name="line-style" select="'solid'"/>

	<!-- font family -->
	<xsl:attribute-set name="font">
		<xsl:attribute name="font-family">Arial</xsl:attribute>
	</xsl:attribute-set>

	<!-- header text -->	
	<xsl:attribute-set name="header-text" use-attribute-sets="font">		
		<xsl:attribute name="font-size">14pt</xsl:attribute>
		<xsl:attribute name="font-style">normal</xsl:attribute>
		<xsl:attribute name="font-weight">bold</xsl:attribute>
	</xsl:attribute-set>

	<!-- footer text -->	
	<xsl:attribute-set name="footer-text" use-attribute-sets="font">		
		<xsl:attribute name="font-size">6pt</xsl:attribute>
		<xsl:attribute name="font-style">normal</xsl:attribute>
		<xsl:attribute name="font-weight">normal</xsl:attribute>
	</xsl:attribute-set>

	<!-- section header text -->	
	<xsl:attribute-set name="section-header-text" use-attribute-sets="font">		
		<xsl:attribute name="font-size">10pt</xsl:attribute>
		<xsl:attribute name="font-style">normal</xsl:attribute>
		<xsl:attribute name="font-weight">bold</xsl:attribute>
	</xsl:attribute-set>

	<!-- regular text -->	
	<xsl:attribute-set name="regular-text" use-attribute-sets="font">		
		<xsl:attribute name="font-size">8pt</xsl:attribute>
		<xsl:attribute name="font-style">normal</xsl:attribute>
		<xsl:attribute name="font-weight">normal</xsl:attribute>
		<xsl:attribute name="text-align">start</xsl:attribute>
	</xsl:attribute-set>

	<!-- Address space size -->
	<xsl:attribute-set name="address-space-size">
		<xsl:attribute name="line-height">1.5in</xsl:attribute>
	</xsl:attribute-set>	

	<!-- Address text -->
	<xsl:attribute-set name="address-text" use-attribute-sets="font">		
		<xsl:attribute name="font-size">10pt</xsl:attribute>
		<xsl:attribute name="font-style">normal</xsl:attribute>
		<xsl:attribute name="font-weight">normal</xsl:attribute>
		<xsl:attribute name="text-align">start</xsl:attribute>
		<xsl:attribute name="line-height">3.0mm</xsl:attribute>
	</xsl:attribute-set>

	<!-- column header -->	
	<xsl:attribute-set name="column-header" use-attribute-sets="font">		
		<xsl:attribute name="font-size">8pt</xsl:attribute>
		<xsl:attribute name="font-style">normal</xsl:attribute>
		<xsl:attribute name="font-weight">bold</xsl:attribute>
		<xsl:attribute name="text-align">start</xsl:attribute>
	</xsl:attribute-set>

	<!-- space size -->	
	<xsl:attribute-set name="space-size">		
		<xsl:attribute name="font-size">3pt</xsl:attribute>
	</xsl:attribute-set>

	<!-- Section header block attr -->
	<xsl:attribute-set name="section-header-block">		
		<xsl:attribute name="background-color">#A8A8A8</xsl:attribute>
		<xsl:attribute name="border-before-style"><xsl:value-of select="$line-style"/></xsl:attribute>
		<xsl:attribute name="border-after-style"><xsl:value-of select="$line-style"/></xsl:attribute>
		<xsl:attribute name="border-before-color"><xsl:value-of select="$line-color"/></xsl:attribute>
		<xsl:attribute name="border-after-color"><xsl:value-of select="$line-color"/></xsl:attribute>
		<xsl:attribute name="border-before-width"><xsl:value-of select="$line-thickness"/></xsl:attribute>
		<xsl:attribute name="border-after-width"><xsl:value-of select="$line-thickness"/></xsl:attribute>
	</xsl:attribute-set>

	<!-- page number header text -->	
	<xsl:attribute-set name="header-page-number-text" use-attribute-sets="font">		
		<xsl:attribute name="font-size">8pt</xsl:attribute>
		<xsl:attribute name="font-style">normal</xsl:attribute>
		<xsl:attribute name="font-weight">bold</xsl:attribute>
		<xsl:attribute name="text-align">end</xsl:attribute>
	</xsl:attribute-set>

	<!-- general start-indent -->	
	<xsl:attribute-set name="start-indent">		
		<xsl:attribute name="start-indent">0.125in</xsl:attribute>
	</xsl:attribute-set>

	<!-- general end-indent -->	
	<xsl:attribute-set name="end-indent">		
		<xsl:attribute name="end-indent">0.125in</xsl:attribute>
	</xsl:attribute-set>

	<!-- general indent -->	
	<xsl:attribute-set name="indent" use-attribute-sets="start-indent end-indent">		
	</xsl:attribute-set>

	<!-- rule properties -->	
	<xsl:attribute-set name="rule" use-attribute-sets="font">		
		<xsl:attribute name="leader-length">100%</xsl:attribute>
		<xsl:attribute name="leader-pattern">rule</xsl:attribute>
		<xsl:attribute name="rule-style"><xsl:value-of select="$line-style"/></xsl:attribute>
		<xsl:attribute name="rule-thickness"><xsl:value-of select="$line-thickness"/></xsl:attribute>
		<xsl:attribute name="color"><xsl:value-of select="$line-color"/></xsl:attribute>
	</xsl:attribute-set>

	<!-- page border properties -->	
	<xsl:attribute-set name="page-border" use-attribute-sets="font">		
		<xsl:attribute name="border-style"><xsl:value-of select="$line-style"/></xsl:attribute>
		<xsl:attribute name="border-width"><xsl:value-of select="$line-thickness"/></xsl:attribute>
		<xsl:attribute name="border-color"><xsl:value-of select="$line-color"/></xsl:attribute>
	</xsl:attribute-set>

	<!-- address border properties -->	
	<xsl:attribute-set name="address-border" use-attribute-sets="font">		
		<xsl:attribute name="border-style"><xsl:value-of select="$line-style"/></xsl:attribute>
		<xsl:attribute name="border-width">0.0mm</xsl:attribute>
		<xsl:attribute name="border-color">#A8A8A8</xsl:attribute>
	</xsl:attribute-set>

</xsl:stylesheet>
