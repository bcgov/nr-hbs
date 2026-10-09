package ca.bc.gov.nrs.hbs.api.dao.v1;

import ca.bc.gov.nrs.hbs.api.exception.HbsApiRuntimeException;

public class StoredProcedureException extends HbsApiRuntimeException {

  private final String packageName;
  private final String procedureName;
  private final String oracleErrorMessage;

  public StoredProcedureException(String packageName, String procedureName, String oracleErrorMessage) {
    super(packageName + "." + procedureName + " failed: " + oracleErrorMessage);
    this.packageName = packageName;
    this.procedureName = procedureName;
    this.oracleErrorMessage = oracleErrorMessage;
  }

  public String getPackageName() {
    return packageName;
  }

  public String getProcedureName() {
    return procedureName;
  }

  public String getOracleErrorMessage() {
    return oracleErrorMessage;
  }
}
