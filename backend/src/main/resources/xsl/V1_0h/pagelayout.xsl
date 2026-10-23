<?xml version="1.0" ?>
<!--
  $RCSfile: pagelayout.xsl,v $ 
  Copyright (c) 2001, B.C. Ministry of Forests. 
  All rights reserved. 
  
  Author:  Chris McCann
  Created: 2002-02-27
	Version: $Revision: 1.1 $ $Author: jdosil $ $Date: 2004/08/31 23:24:48 $

	The page layout.  Setup the sizes of the different regions for both reports and invoices.
	Notice the different sizes of the footer region for invoices and statements
-->
	<xsl:stylesheet 
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	xmlns:fo="http://www.w3.org/1999/XSL/Format"
	version="1.0">
	<xsl:output method="xml" indent="yes"/>

	<xsl:template name="page-layout">
		<fo:layout-master-set>
			<!-- Invoice Page layout -->
			<fo:simple-page-master 
				master-name="invoice"
				page-height="11in"
				page-width="8.5in" 
				margin-top="0.25in" 
				margin-bottom="0.25in" 
				margin-left="0.25in" 
				margin-right="0.25in">

				<fo:region-body 
					region-name="xsl-region-body"
					margin-top="0.8in" 
					margin-bottom="0.90in" 
					margin-left="0.0in"
					margin-right="0.0in" />

				<fo:region-before
					region-name="xsl-region-before"
					precedence="true" 
					extent="0.8in"/>

				<fo:region-after 
					region-name="xsl-region-after"
					precedence="true"
					extent="0.90in"/>

				<fo:region-start
					precedence="true" 
					region-name="xsl-region-start" 
					extent="7.5in"
					margin-top="0.5in" 
					margin-bottom="0.5in" 
					margin-left="0.5in" 
					margin-right="0.5in" />

				<fo:region-end
					precedence="true" 
					border="solid" 
					region-name="xsl-region-end"
					extent="0.0in"/>
			</fo:simple-page-master>

			<!-- Statement page layout  eg. Volume/Ratio/Compiled Sample Statements-->
			<fo:simple-page-master 
				master-name="statement"
				page-height="11in"
				page-width="8.5in" 
				margin-top="0.25in" 
				margin-bottom="0.25in" 
				margin-left="0.25in" 
				margin-right="0.25in">

				<fo:region-body 
					region-name="xsl-region-body"
					margin-top="0.8in" 
					margin-bottom="0.35in" 
					margin-left="0.0in"
					margin-right="0.0in" />

				<fo:region-before
					region-name="xsl-region-before"
					precedence="true" 
					extent="0.8in"/>

				<fo:region-after 
					region-name="xsl-region-after"
					precedence="true"
					extent="0.5in"/>

				<fo:region-start
					precedence="true" 
					region-name="xsl-region-start" 
					extent="7.5in"
					margin-top="0.5in" 
					margin-bottom="0.5in" 
					margin-left="0.5in" 
					margin-right="0.5in" />

				<fo:region-end
					precedence="true" 
					border="solid" 
					region-name="xsl-region-end"
					extent="0.0in"/>
			</fo:simple-page-master>
		</fo:layout-master-set>
	</xsl:template>
</xsl:stylesheet>
