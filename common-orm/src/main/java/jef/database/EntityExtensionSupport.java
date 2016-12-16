package jef.database;

import java.util.HashMap;
import java.util.Map;

import jef.common.log.LogUtil;
import jef.database.dialect.type.ColumnMapping;
import jef.database.meta.ExtensionConfig;
import jef.database.meta.ExtensionConfigFactory;
import jef.database.meta.ITableMetadata;
import jef.database.meta.extension.EfPropertiesExtensionProvider;

/**
 * 扩展属性支持
 * 
 * @author jiyi
 * 
 */
public abstract class EntityExtensionSupport extends DataObject implements MetadataContainer {
	private static final long serialVersionUID = 5516138408171716254L;
	private transient Map<String, Object> attributes;
	// 扩展点信息
	private ExtensionConfigFactory extensionFactory;
	// 绑定后的表结构
	private ExtensionConfig config;

	public EntityExtensionSupport() {
		this.extensionFactory = EfPropertiesExtensionProvider.getInstance().getEF(this.getClass());
		if (extensionFactory == null) {
			LogUtil.error(this.getClass());
		}
		this.config = extensionFactory.getDefault();
	}

	public ITableMetadata getMeta() {
		if (config == null) {
			config = extensionFactory.getExtension(this);
		}
		return config.getMeta();
	}

	/**
	 * 动态扩展
	 * 
	 * @param typeName
	 */
	public EntityExtensionSupport(String typeName) {
		this.extensionFactory = EfPropertiesExtensionProvider.getInstance().getEF(this.getClass());
		if (extensionFactory == null) {
			throw new IllegalArgumentException();
		}
		this.config = extensionFactory.getExtension(typeName);
	}

	/**
	 * 设置扩展属性
	 * 
	 * @param prop
	 * @param value
	 */
	public void setAtribute(String key, Object value) {
		if (attributes == null) {
			attributes = new HashMap<String, Object>();
		}
		ITableMetadata meta = this.getMeta();
		ColumnMapping field = meta.getExtendedColumnDef(key);
		if (field == null) {
			throw new IllegalArgumentException("Unknown [" + key + "] .Avaliable: " + getMeta().getAllFieldNames());
		} else {
			// Check the data type
			Class<?> expected = field.getFieldType();
			if (value != null && !expected.isAssignableFrom(value.getClass())) {
				throw new IllegalArgumentException("Field value invalid for the data type of column. field name '" + key + "' value is a '" + value.getClass().getSimpleName() + "'. expected is " + expected.getSimpleName());
			}
//			if (_recordUpdate)
//				super.markUpdateFlag(field.field(), value);
		}
		attributes.put(key, value);
	}

	/**
	 * 获取扩展属性
	 * 
	 * @param prop
	 * @return
	 */
	public Object getAtribute(String key) {
		super.beforeGet("attributes");
		if (attributes == null) {
			return null;
		}
		return attributes.get(key);
	}
}
