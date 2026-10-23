<?xml version="1.0" ?>
<!--
  $RCSfile: invoice-footer.xsl,v $ 
  Copyright (c) 2001, B.C. Ministry of Forests. 
  All rights reserved. 
  
  Author:  Chris McCann
  Created: 2002-02-27
	Version: $Revision: 1.1 $ $Author: CWMcCann $ $Date: 2003/07/02 21:48:55 $

	Invoice Footer
-->
<xsl:stylesheet 
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	xmlns:fo="http://www.w3.org/1999/XSL/Format"
	version="1.0">
	
	<xsl:output method="xml" indent="yes"/>

	<!-- 
		Simple template that inserts the footer comments for invoices into
		the static region after
	-->		
	<xsl:template name="footer">
	<fo:static-content flow-name="xsl-region-after">
		<fo:block xsl:use-attribute-sets="footer-text indent">
				<fo:leader xsl:use-attribute-sets="rule"/>
				<fo:block text-align="justify">
					Payable on receipt. This amount will appear on your next Statement of 
					Account. If you do not pay your account in full, then interest will be
					charged at a prescribed rate on the unpaid Statement balance as at the 
					due date specified on the Statement of Account. Please quote customer 
					account and invoice number with your payment. Please make cheque 
					payable to the Minister of Finance, and make payment to any Ministry of
					Provincial Revenue office listed on your Forest Statement of Account. All
					charges on this invoice are GST exempt unless otherwise indicated.
				</fo:block>
				<fo:block text-align="start">
					Please be advised that this invoice and information may be released 
					under the Freedom of Information and Protection of Privacy Act.
				</fo:block>
				<fo:block text-align="center" font-weight="bold" start-indent="0.50in" end-indent="0.50in">
					NOTICE:	This invoice or a copy of this invoice is being sent to you to
					notify you that if this invoice is not paid by the licensee (or a party
					on behalf of the licensee),	then the Ministry may seek payment from you
					pursuant to Sections 130 and 131 of the Forest Act.
					&#160;&#160;&#160;&#160;&#160;&#160;Provincial GST registration number: R107864738
				</fo:block>
			</fo:block>
		</fo:static-content>
	</xsl:template>
</xsl:stylesheet>
