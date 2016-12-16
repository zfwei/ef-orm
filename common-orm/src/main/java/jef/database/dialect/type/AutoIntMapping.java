package jef.database.dialect.type;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import jef.accelerator.bean.AbstractFastProperty;
import jef.database.Field;
import jef.database.dialect.ColumnType;
import jef.database.dialect.DatabaseDialect;
import jef.database.jdbc.result.IResultSet;
import jef.database.meta.ITableMetadata;
import jef.tools.reflect.Property;

public final class AutoIntMapping extends AutoIncrementMapping {
	@Override
	public void init(Field field, String columnName, ColumnType type, ITableMetadata meta) {
		super.init(field, columnName, type, meta);
		accessor = new J2IProperty(super.fieldAccessor);
	}

	public Object jdbcSet(PreparedStatement st, Object value, int index, DatabaseDialect session) throws SQLException {
		if (value == null) {
			st.setNull(index, getSqlType());
		} else {
			st.setInt(index, ((Number) value).intValue());
		}
		return value;
	}

	public Object jdbcGet(IResultSet rs, int n) throws SQLException {
		Object obj = rs.getObject(n);
		if (obj == null)
			return null;
		if (obj instanceof Integer)
			return obj;
		return ((Number) obj).intValue();
	}

	public void jdbcUpdate(ResultSet rs,String column, Object value, DatabaseDialect dialect) throws SQLException {
		rs.updateInt(column, ((Number) value).intValue());
	}
	
	@Override
	protected String getSqlExpression(Object value, DatabaseDialect profile) {
		return value.toString();
	}

	private static class J2IProperty extends AbstractFastProperty {
		private Property sProperty;

		J2IProperty(Property inner) {
			this.sProperty = inner;
		}

		public String getName() {
			return sProperty.getName();
		}

		public Object get(Object obj) {
			Integer s = (Integer) sProperty.get(obj);
			if (s == null)
				return null;
			return s.longValue();
		}

		public void set(Object obj, Object value) {
			if (value != null) {
				value = ((Number) value).intValue();
			}
			sProperty.set(obj, value);
		}
	}

	@Override
	protected Class<?> getDefaultJavaType() {
		return Integer.class;
	}

}
