/*
 * JEF - Copyright 2009-2010 Jiyi (mr.jiyi@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jef.database.meta;

import jef.database.dialect.ColumnType;
import jef.database.dialect.DatabaseDialect;
import jef.database.query.SqlExpression;
import jef.tools.StringUtils;



public class Column{
	private int ordinal;
	private String tableName;
	private String columnName;
	private String remarks;
	private String dataType;
	/**
	 * java.sql.Types中的常量之一
	 */
	private int dataTypeCode;
	private int columnSize;
	private int decimalDigit;
	private boolean nullable;
	private String columnDef;
	/**
	 * 是否unique，数据库支持 unique index和 unique constraint两种方式实现。
	 * 当然，在大部分些数据库上其实是同一种实现方式。
	 */
	private boolean unique;
	
	public ColumnType toColumnType(DatabaseDialect profile){
		ColumnType ct=profile.getProprtMetaFromDbType(this);
		ct.setNullable(nullable);
		if(StringUtils.isNotEmpty(columnDef)){
			ct.setDefault(new SqlExpression(columnDef));
		}
		//System.out.println(this.dataType+" -> "+ ct.toString());
		return ct;
	}
	
	public int getColumnSize() {
		return columnSize;
	}
	public void setColumnSize(int columnSize) {
		this.columnSize = columnSize;
	}
	public int getDataTypeCode() {
		return dataTypeCode;
	}
	public void setDataTypeCode(int dataTypeCode) {
		this.dataTypeCode = dataTypeCode;
	}
	public boolean isNullable() {
		return nullable;
	}
	public void setNullable(boolean nullAble) {
		this.nullable = nullAble;
	}
	public String getColumnName() {
		return columnName;
	}
	public void setColumnName(String columnName) {
		this.columnName = columnName;
	}
	public String getDataType() {
		return dataType;
	}
	public void setDataType(String dataType) {
		this.dataType = dataType;
	}
	public String getRemarks() {
		return remarks;
	}
	public void setRemarks(String remarks) {
		this.remarks = remarks;
	}
	public String getTableName() {
		return tableName;
	}
	public void setTableName(String tableName) {
		this.tableName = tableName;
	}
	public int getDecimalDigit() {
		return decimalDigit;
	}
	public void setDecimalDigit(int decimalDigit) {
		this.decimalDigit = decimalDigit;
	}
	public String getColumnDef() {
		return columnDef;
	}
	public void setColumnDef(String columnDef) {
		this.columnDef = columnDef;
	}
	public int getOrdinal() {
		return ordinal;
	}
	public void setOrdinal(int ordinal) {
		this.ordinal = ordinal;
	}
	public boolean isUnique() {
		return unique;
	}

	public void setUnique(boolean unique) {
		this.unique = unique;
	}

	@Override
	public String toString() {
		StringBuilder sb=new StringBuilder(columnName);
		sb.append(" ").append(this.dataType);
		if(!this.nullable){
			sb.append(" not null");
		}
		if(unique){
			sb.append(" unique");
		}
		return sb.toString();
	}
}
