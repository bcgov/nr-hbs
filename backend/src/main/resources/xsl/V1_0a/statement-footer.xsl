<?xml version="1.0" ?>
<!--
  $RCSfile: statement-footer.xsl,v $ 
  Copyright (c) 2001, B.C. Ministry of Forests. 
  All rights reserved. 

	Author:  Chris McCann
	Created: 2002-02-27
	Version: $Revision: 1.1 $ $Author: CWMcCann $ $Date: 2003/07/23 23:11:16 $

	Footer for non-invoice reports
	-->
	<xsl:stylesheet 
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	xmlns:fo="http://www.w3.org/1999/XSL/Format"
	version="1.0">

	<xsl:output method="xml" indent="yes"/>

	<xsl:template name="statement-footer">
		<fo:static-content flow-name="xsl-region-after">
			<fo:block xsl:use-attribute-sets="footer-text indent">
				<fo:leader xsl:use-attribute-sets="rule"/>
				<fo:block text-align="justify">
					Please be advised that this volume statement and information may
					be released under the Freedom of Information and Protection of Privacy Act.
				</fo:block>
			</fo:block>
		</fo:static-content>
	</xsl:template>
</xsl:stylesheet>
