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

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.persistence.Column;
import javax.persistence.JoinColumn;
import javax.persistence.JoinColumns;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.NoResultException;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.OrderBy;
import javax.persistence.PersistenceException;
import javax.persistence.Transient;

import jef.accelerator.asm.Attribute;
import jef.accelerator.asm.ClassReader;
import jef.accelerator.asm.ClassVisitor;
import jef.accelerator.asm.Opcodes;
import jef.common.log.LogUtil;
import jef.common.wrapper.Holder;
import jef.database.Condition.Operator;
import jef.database.DbCfg;
import jef.database.DbMetaData;
import jef.database.DbUtils;
import jef.database.EntityExtensionSupport;
import jef.database.Field;
import jef.database.IQueryableEntity;
import jef.database.JefClassLoader;
import jef.database.MetadataContainer;
import jef.database.ORMConfig;
import jef.database.PojoWrapper;
import jef.database.Session;
import jef.database.VarObject;
import jef.database.annotation.Cascade;
import jef.database.annotation.DynamicKeyValueExtension;
import jef.database.annotation.DynamicTable;
import jef.database.annotation.EasyEntity;
import jef.database.annotation.FieldOfTargetEntity;
import jef.database.annotation.Indexed;
import jef.database.annotation.JoinDescription;
import jef.database.annotation.JoinType;
import jef.database.annotation.NoForceEnhance;
import jef.database.dialect.ColumnType;
import jef.database.dialect.ColumnType.AutoIncrement;
import jef.database.dialect.ColumnType.GUID;
import jef.database.dialect.type.ColumnMapping;
import jef.database.dialect.type.ColumnMappings;
import jef.database.jsqlparser.expression.BinaryExpression;
import jef.database.jsqlparser.parser.ParseException;
import jef.database.jsqlparser.visitor.Expression;
import jef.database.jsqlparser.visitor.ExpressionType;
import jef.database.meta.AnnotationProvider.ClassAnnotationProvider;
import jef.database.meta.AnnotationProvider.FieldAnnotationProvider;
import jef.database.meta.def.IndexDef;
import jef.database.meta.extension.EfPropertiesExtensionProvider;
import jef.database.query.JpqlExpression;
import jef.database.query.ReadOnlyQuery;
import jef.database.support.EntityNotEnhancedException;
import jef.database.support.QuerableEntityScanner;
import jef.tools.ArrayUtils;
import jef.tools.Assert;
import jef.tools.IOUtils;
import jef.tools.JefConfiguration;
import jef.tools.StringUtils;
import jef.tools.collection.CollectionUtils;
import jef.tools.reflect.BeanUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ArrayListMultimap;

/**
 * 静态存放所有数据表元模型的存放类。
 * 
 * 各个数据表模型都可以从这个类的方法中得到。
 * 
 * @author jiyi
 * 
 */
@SuppressWarnings("deprecation")
public final class MetaHolder {
	private MetaHolder() {
	}

	// 分表分库策略加载器
	static private PartitionStrategyLoader partitionLoader;
	// 元数据加载器
	static MetadataConfiguration config;
	// Schema映射
	static Map<String, String> SCHEMA_MAPPING;
	// 站点映射
	static Map<String, String> SITE_MAPPING;

	// 元数据池（包含标准Entity的元数据和POJO的元数据）
	static final Map<Class<?>, AbstractMetadata> pool = new java.util.IdentityHashMap<Class<?>, AbstractMetadata>(32);
	// 动态表元数据池
	static final Map<String, TupleMetadata> dynPool = new java.util.HashMap<String, TupleMetadata>(32);
	// 反向查找表
	private static final Map<String, AbstractMetadata> inverseMapping = new HashMap<String, AbstractMetadata>();

	private static Logger log = LoggerFactory.getLogger(MetaHolder.class);

	// 初始化分表规则加载器
	static {
		try {
			String clz = JefConfiguration.get(DbCfg.PARTITION_STRATEGY_LOADER);
			if (StringUtils.isNotEmpty(clz)) {
				partitionLoader = (PartitionStrategyLoader) BeanUtils.newInstance(clz);
			}
			if (partitionLoader == null) {
				partitionLoader = new DefaultPartitionStrategyLoader();
			}
		} catch (Exception e) {
			log.error("PARTITION_STRATEGY_LOADER error", e);
		}
		try {
			String clz = JefConfiguration.get(DbCfg.CUSTOM_METADATA_LOADER);
			if (StringUtils.isNotEmpty(clz)) {
				config = (MetadataConfiguration) BeanUtils.newInstance(clz);
			}
			if (config == null) {
				config = new DefaultMetaLoader();
			}
		} catch (Exception e) {
			log.error("CUSTOM_METADATA_LOADER error", e);
		}
		try {
			SCHEMA_MAPPING = StringUtils.toMap(JefConfiguration.get(DbCfg.SCHEMA_MAPPING), ",", ":", 1);
			String configStr = JefConfiguration.get(DbCfg.DB_DATASOURCE_MAPPING);
			SITE_MAPPING = StringUtils.toMap(configStr, ",", ":", -1);
			if (!SITE_MAPPING.isEmpty()) {
				LogUtil.info("Database mapping: " + SITE_MAPPING);
			}
		} catch (Exception e) {
			log.error("SCHEMA_MAPPING error", e);
		}
	}

	/**
	 * 获得重定向后的Schema
	 * 
	 * @param key
	 * @return 如果返回""，表示无schema 如果返回入参本身，表示不作改动 其他情况，返回重定向的schema
	 */
	public static String getMappingSchema(String key) {
		if (key == null)
			return null;
		String result = SCHEMA_MAPPING.get(key.toUpperCase());
		if (result == null)
			return key;
		return result.length() == 0 ? null : result;
	}

	/**
	 * 获得重定向后的Site
	 * 
	 * @param key
	 * @return 如果返回""，表示无Site 如果返回入参本身，表示不作改动 其他情况，返回重定向的Site
	 */
	public static String getMappingSite(String key) {
		if (key == null)
			return null;
		String result = SITE_MAPPING.get(key.toLowerCase());
		if (result == null)
			return key;
		return result.length() == 0 ? null : result;
	}

	/**
	 * 根据数据库的表情况，初始化动态表的模型 初始化完成后会缓存起来，下次获取可以直接用{@link #getMeta(String)}得到
	 * 
	 * @param session
	 *            数据库访问句柄 Session.
	 * @param tableName
	 *            表名
	 * @return
	 */
	public static TupleMetadata initMetadata(Session session, String tableName) throws SQLException {
		return initMetadata(session, tableName, true);
	}

	/**
	 * 根据数据库的表情况，初始化动态表的模型 初始化完成后会缓存起来，下次获取可以直接用{@link #getMeta(String)}得到
	 * 
	 * @param session
	 *            数据库访问句柄 Session.
	 * @param tableName
	 *            表名
	 * @param convertColumnNames
	 *            是否将数据库的列名转换为 java 习惯。 <br/>
	 *            eg. CREATE_TIME -> createTime
	 * @return
	 */
	public static TupleMetadata initMetadata(Session session, String tableName, boolean convertColumnNames) throws SQLException {
		DbMetaData meta = session.getNoTransactionSession().getMetaData(null);
		List<TableInfo> table = meta.getTable(tableName);
		if (table.isEmpty()) {
			throw new SQLException("The table " + tableName + " does not exit in database " + session.getNoTransactionSession().toString());
		}
		PrimaryKey pks = meta.getPrimaryKey(tableName);
		List<jef.database.meta.Column> columns = meta.getColumns(tableName, false);
		TupleMetadata m = new TupleMetadata(tableName);
		for (jef.database.meta.Column c : columns) {
			boolean isPk = (pks == null) ? false : pks.hasColumn(c.getColumnName());
			// m.addColumn(c.getColumnName(), c.getColumnName(),
			// c.toColumnType(meta.getProfile()), isPk);
			m.addColumn(DbUtils.underlineToUpper(c.getColumnName(), false), c.getColumnName(), c.toColumnType(meta.getProfile()), isPk);
		}
		putDynamicMeta(m);
		return m;
	}

	/**
	 * 放置动态表的模型
	 * 
	 * @param meta
	 */
	public static void putDynamicMeta(TupleMetadata meta) {
		String name = meta.getTableName(true).toUpperCase();
		TupleMetadata old = dynPool.put(name, meta);
		if (old != null) {
			LogUtil.warn("replace tuple metadata:{}", name);
		}
	}

	/**
	 * 返回动态表的模型
	 * 
	 * @param name
	 * @return
	 */
	public static TupleMetadata getDynamicMeta(String name) {
		if (name == null)
			return null;
		return dynPool.get(name.toUpperCase());
	}

	/**
	 * 初始化数据，可以指定schema和tablename
	 * 
	 * @param clz
	 *            实体类
	 * @param schema
	 *            传入null表示不修改默认的schema，传""表示修改为当前数据库schema，传入其他则为指定的schema
	 * @param tablename
	 *            传入null表示不修正
	 * @return
	 */
	public static ITableMetadata initMetadata(Class<? extends IQueryableEntity> clz, String schema, String tablename) {
		Assert.notNull(clz);
		ITableMetadata me = (TableMetadata) pool.get(clz);
		// initData方法会处理关于缓存的问题
		me = initData(clz);
		if (me instanceof TableMetadata) {
			TableMetadata m = (TableMetadata) me;
			if (schema != null)
				m.setSchema(getMappingSchema(schema));
			if (StringUtils.isNotEmpty(tablename))
				m.setTableName(tablename);
		}
		return me;
	}

	/**
	 * 将一个对象名（数据表、索引等）转换为schemaMapping后的名称
	 * 
	 * @param objectName
	 */
	public static String toSchemaAdjustedName(String objectName) {
		if (objectName == null) {
			return null;
		}

		int n = objectName.indexOf('.');
		if (n < 0)
			return objectName;
		String schema = objectName.substring(0, n);
		String schema1 = MetaHolder.getMappingSchema(schema);
		if (schema == schema1) {
			return objectName;
		}
		return schema1 == null ? objectName.substring(n + 1) : schema1.concat(objectName.substring(n));
	}

	/**
	 * 获取所有已经缓存的动态表模型
	 * 
	 * @return
	 */
	public static Collection<TupleMetadata> getCachedDynamicModels() {
		return dynPool.values();
	}

	/**
	 * 获取所有已经缓存的静态表模型
	 */
	public static Collection<AbstractMetadata> getCachedModels() {
		return pool.values();
	}

	/**
	 * 得到指定类的元数据。该类可以是一个标准的GeeQueryEntity，也可以是一个POJO。
	 * 
	 * @param clz
	 * @return
	 */
	public static final AbstractMetadata getMetaOrTemplate(Class<?> clz) {
		Assert.notNull(clz);
		if (clz == VarObject.class) {
			throw new IllegalArgumentException("A VarObject class does not indicted to any table metadata.");
		}
		AbstractMetadata m = pool.get(clz);
		if (m == null) {
			m = initData(clz);
		}
		return m;
	}

	/**
	 * 根据类获取表模型
	 * 
	 * @param clz
	 * @return
	 */
	public static final AbstractMetadata getMeta(Class<?> clz) {
		Assert.notNull(clz);
		if (clz == VarObject.class) {
			throw new IllegalArgumentException("A VarObject class does not indicted to any table metadata.");
		}
		AbstractMetadata m = pool.get(clz);
		if (m == null) {
			m = initData(clz);
		}
		if (m.getType() == EntityType.TEMPLATE) {
			throw new IllegalArgumentException("A Template class does not indicted to any table metadata.");
		}
		return m;
	}

	/**
	 * 获取metadata
	 * 
	 * @param d
	 * @return
	 */
	public final static AbstractMetadata getMeta(Object d) {
		if (d instanceof MetadataContainer) {
			return (AbstractMetadata) ((MetadataContainer) d).getMeta();
		}
		AbstractMetadata metadata = getMeta(d.getClass());
		if (metadata.getType() == EntityType.TEMPLATE) {
			return metadata.getExtension((IQueryableEntity) d).getMeta();
		}
		return metadata;
	}

	private static boolean isFirstInterfaceClzEntity(Class<?> sc) {
		Class<?>[] interfaces = sc.getInterfaces();
		if (interfaces == null || interfaces.length == 0)
			return false;
		return ArrayUtils.contains(interfaces, IQueryableEntity.class);
	}

	/**
	 * 在获取类时，需要有一个标记快速判断该类是否经过增强（无论是动态增强还是静态增强）一旦发现没增强的类，就抛出异常�?
	 * 
	 * @param clz
	 * @return
	 */
	private synchronized static AbstractMetadata initData(Class<?> clz) {
		{
			AbstractMetadata m1 = pool.get(clz);
			if (m1 != null)
				return m1; // 双重检查锁定
		}
		if (IQueryableEntity.class.isAssignableFrom(clz)) {
			// 计算动态扩展字段
			DynamicTable dt = clz.getAnnotation(DynamicTable.class);
			DynamicKeyValueExtension dkv = clz.getAnnotation(DynamicKeyValueExtension.class);
			if (dt == null && dkv == null) {// 两种扩展方式只能出现一种
				return initEntity(clz.asSubclass(IQueryableEntity.class));
			} else if (dt != null) {
				return initVarTemplate(clz.asSubclass(EntityExtensionSupport.class), dt);
			} else if (dkv != null) {
				return initVarEntity(clz.asSubclass(EntityExtensionSupport.class), dkv);
			} else {
				throw new UnsupportedOperationException("Not support @DynamicTable and @DynamicKeyValueExtension simultaneously.");
			}
		} else {
			return initPojo(clz);
		}
	}

	private static AbstractMetadata initVarTemplate(Class<? extends EntityExtensionSupport> clz, DynamicTable dt) {
		ClassAnnotationProvider annos = config.getAnnotations(clz);
		List<java.lang.reflect.Field> unprocessedField = new ArrayList<java.lang.reflect.Field>();
		TableMetadata meta = internalProcess(clz, unprocessedField, annos);
		// 加载分表策略
		Assert.notNull(partitionLoader, "the Partition loader is null!");
		if (partitionLoader.get(clz) != null) {
			throw new UnsupportedOperationException("Not support a dynamic template with partition.");
		}

		ExtensionTemplate ef = new ExtensionTemplate(dt, clz, meta);
		EfPropertiesExtensionProvider.getInstance().register(clz, ef);
		TemplateMetadata result = new TemplateMetadata(ef);
		result.setUnprocessedFields(unprocessedField, annos);
		pool.put(clz, result);
		return result;
	}

	private static AbstractMetadata initVarEntity(Class<? extends EntityExtensionSupport> clz, DynamicKeyValueExtension dkv) {
		ClassAnnotationProvider annos = config.getAnnotations(clz);
		List<java.lang.reflect.Field> unprocessedField = new ArrayList<java.lang.reflect.Field>();
		TableMetadata raw = internalProcess(clz, unprocessedField, annos);

		// 加载分表策略
		Assert.notNull(partitionLoader, "the Partition loader is null!");
		raw.setPartition(partitionLoader.get(clz));

		KvExtensionImpl ef = new KvExtensionImpl(dkv, clz, raw);
		EfPropertiesExtensionProvider.getInstance().register(clz, ef);
		AbstractMetadata meta = ef.getDefault().getMeta();

		// 此时就将基本字段计算完成的元数据加入缓存，以免在多表关系处理时遭遇死循环
		pool.put(clz, meta);
		ef.initMeta();

		// 针对未处理的字段，当做外部引用关系处理
		for (java.lang.reflect.Field f : unprocessedField) {
			// 将这个字段作为外部引用处理
			processReference(meta, annos.forField(f));
		}
		return meta;
	}

	private static AbstractMetadata initPojo(Class<?> clz) {
		ClassAnnotationProvider annos = config.getAnnotations(clz);
		TableMetadata meta = new TableMetadata(PojoWrapper.class, clz, annos);

		List<java.lang.reflect.Field> unprocessedField = new ArrayList<java.lang.reflect.Field>();

		MeteModelFields metaFields = new MeteModelFields(clz, meta);

		Class<?> processingClz = clz;
		while (processingClz != Object.class) {
			processMetaForClz(processingClz, unprocessedField, meta, annos, metaFields);
			processingClz = processingClz.getSuperclass();
			if (isFirstInterfaceClzEntity(processingClz)) {
				break;
			}
		}
		metaFields.check();
		return meta;
	}

	private static AbstractMetadata initEntity(Class<? extends IQueryableEntity> clz) {
		ClassAnnotationProvider annos = config.getAnnotations(clz);
		List<java.lang.reflect.Field> unprocessedField = new ArrayList<java.lang.reflect.Field>();
		TableMetadata meta = internalProcess(clz, unprocessedField, annos);
		// 加载分表策略
		Assert.notNull(partitionLoader, "the Partition loader is null!");
		meta.setPartition(partitionLoader.get(clz));

		// 此时就将基本字段计算完成的元数据加入缓存，以免在多表关系处理时遭遇死循环
		pool.put(clz, meta);
		// 针对未处理的字段，当做外部引用关系处理
		for (java.lang.reflect.Field f : unprocessedField) {
			// 将这个字段作为外部引用处理
			processReference(meta, annos.forField(f));
			// 还有一种情况，即定义了Column注解，但不属于元模型的一个字段，用于辅助映射的。当结果拼装时有用
			processColumnHelper(meta, annos.forField(f));
		}
		return meta;
	}

	private static TableMetadata internalProcess(Class<? extends IQueryableEntity> clz, List<java.lang.reflect.Field> unprocessedField, ClassAnnotationProvider annos) {
		{
			EasyEntity ee = annos.getAnnotation(EasyEntity.class);
			if (ORMConfig.getInstance().isCheckEnhancement()) {
				assertEnhanced(clz, ee, annos);
			}
		}
		TableMetadata meta = new TableMetadata(clz, annos);
		MeteModelFields metaFields = new MeteModelFields(clz, meta);

		Class<?> processingClz = clz;
		while (processingClz != Object.class) {
			processMetaForClz(processingClz, unprocessedField, meta, annos, metaFields);
			if (processingClz != clz) {
				meta.addParent(processingClz);
			}
			processingClz = processingClz.getSuperclass();// 父类:下一个要解析的类
			if (isFirstInterfaceClzEntity(processingClz)) {
				break;
			}
		}
		metaFields.check();
		return meta;
	}

	// 处理非元模型的Column描述字段
	private static void processColumnHelper(TableMetadata meta, FieldAnnotationProvider field) {
		Column column = field.getAnnotation(Column.class);
		if (column != null) {
			meta.addNonMetaModelFieldMapping(field.getName(), column);
		}
	}

	/**
	 * 检查是否执行了增强
	 * 
	 * @param type
	 */
	private static void assertEnhanced(Class<? extends IQueryableEntity> type, EasyEntity ee, AnnotationProvider annos) {
		if (annos.getAnnotation(NoForceEnhance.class) != null) {
			return;
		}
		if (ee != null && ee.checkEnhanced() == false) {
			return;
		}
		// 如果实体扫描时作了动态增强的话
		if (QuerableEntityScanner.dynamicEnhanced.contains(type.getName())) {
			return;
		}
		// 仅需对非JefClassLoader加载的类做check.
		if (type.getClassLoader().getClass().getName().equals(JefClassLoader.class.getName())) {
			return;
		}
		String resourceName = type.getName().replace('.', '/') + ".class";
		URL url = type.getClassLoader().getResource(resourceName);
		if (url == null) {
			LogUtil.warn("The source of class " + type + " not found, skip enhanced-check.");
			return;
		}
		byte[] data;
		try {
			data = IOUtils.toByteArray(url);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		ClassReader cr = new ClassReader(data);

		final Holder<Boolean> checkd = new Holder<Boolean>(false);
		cr.accept(new ClassVisitor(Opcodes.ASM5) {
			public void visitAttribute(Attribute attr) {
				if ("jefd".equals(attr.type)) {
					checkd.set(true);
				}
				super.visitAttribute(attr);
			}
		}, ClassReader.SKIP_CODE);
		if (!checkd.get()) {
			throw new EntityNotEnhancedException(type.getName());
		}
	}

	static class MeteModelFields {
		private boolean isTuple;
		ArrayListMultimap<String, Field> enumFields;
		private ITableMetadata parent;

		MeteModelFields(Class<?> clz, ITableMetadata meta) {
			isTuple = !IQueryableEntity.class.isAssignableFrom(clz);
			parent = meta;

			if (isTuple)
				return;
			enumFields = com.google.common.collect.ArrayListMultimap.create();
			Class<?> looping = clz;
			while (looping != Object.class) {
				for (Class<?> c : looping.getDeclaredClasses()) {
					if (c.isEnum() && ArrayUtils.contains(c.getInterfaces(), jef.database.Field.class)) {
						@SuppressWarnings("rawtypes")
						Class<? extends Enum> sub = c.asSubclass(Enum.class);
						for (Enum<?> fieldDef : sub.getEnumConstants()) {
							Field field = (Field) fieldDef;
							enumFields.put(field.name(), field);// 父类的放在后面，子类的放在前面。
						}
						break;
					}
				}
				looping = looping.getSuperclass();
			}
		}

		public void check() {
			if (hasMappingFailure()) {
				throw new IllegalArgumentException("These meta model field is not exist in [" + parent.getName() + "]:" + enumFields.keySet());
			}
		}

		/**
		 * 由于用户可能在父子类中反复定义同一个Field的元模型，因此此处返回的是列表
		 * @param name
		 * @return
		 */
		List<jef.database.Field> remove(String name) {
			if (isTuple) {
				return Collections.<Field> singletonList(new TupleField(parent, name));
			}
			return enumFields.removeAll(name);
		}

		boolean hasMappingFailure() {
			if (isTuple) {
				return false;
			} else {
				return !enumFields.isEmpty();
			}
		}
	}

	private static void processMetaForClz(Class<?> processingClz, List<java.lang.reflect.Field> unprocessedField, TableMetadata meta, ClassAnnotationProvider annos, MeteModelFields metaModel) {
		for (java.lang.reflect.Field f : processingClz.getDeclaredFields()) {
			if (Modifier.isStatic(f.getModifiers()))
				continue;
			if (meta.getField(f.getName()) != null) { // 当子类父类中有同名field时，跳过父类的field
				continue;
			}
			FieldAnnotationProvider fa = annos.forField(f);
			List<Field> fieldss = metaModel.remove(f.getName());
			if (fieldss.isEmpty() || fa.getAnnotation(Transient.class)!=null) {
				unprocessedField.add(f);
				continue;
			}
			Field field = fieldss.get(0);
			if (field instanceof Enum) {
				assertFieldEnhanced(field,fieldss,processingClz);
			}

	
			// 在得到了元模型的情况下
			boolean isPK = fa.getAnnotation(javax.persistence.Id.class) != null;
			
			jef.database.annotation.Type customType = fa.getAnnotation(jef.database.annotation.Type.class);
			ColumnMapping type = null;
			Class<?> fieldType;
			if (customType != null) {
				type = BeanUtils.newInstance(customType.value());
				try {
					ColumnTypeBuilder.applyParams(customType.parameters(), type);
				} catch (Exception e) {
					throw new IllegalArgumentException("@Type annotation on field " + processingClz + "." + field + " is invalid", e);
				}
				fieldType = type.getFieldType();
			} else {
				fieldType = f.getType();
			}
			Column c = fa.getAnnotation(Column.class);
			ColumnType ct;
			try {
				ct = new ColumnTypeBuilder(c, f, fieldType, fa).withCustomType(type).build();
			} catch (Exception e) {
				throw new PersistenceException(processingClz + " has invalid field/column " + f.getName(), e);
			}

			if (isPK)
				ct.setNullable(false);

			try {
				String columnName = getColumnName(field, c);
				if (type == null) {
					type = ColumnMappings.getMapping(field, meta, columnName, ct, false);
				} else {
					type.init(field, columnName, ct, meta);
				}
				meta.putJavaField(field, type, columnName, isPK);
			} catch (IllegalArgumentException e) {
				throw new PersistenceException(meta.getName() + ":" + field.name() + " can not mapping to sql type.", e);
			}

			// 设置索引
			Indexed i = fa.getAnnotation(Indexed.class);// 单列索引
			if (i != null) {
				IndexDef indexDef = new IndexDef(i.name(), new String[] { i.desc() ? field.name() + " desc" : field.name() });
				indexDef.setUnique(i.unique());
				indexDef.setDefinition(i.definition());
				meta.indexes.add(indexDef);
			}
		}
	}

	private static void assertFieldEnhanced(Field field, List<Field> fieldss,Class<?> processingClz) {

		/*
		 * 必须至少有一个meta field的定义类==
		 * processingClz。这样才能保证这个属性被增强过。否则不能保证该属性被增强过。
		 * 
		 * 因为目前增强算法都只按当前类的enum Field中的枚举来增强属性。不会去增强父类中的属性。
		 * 所以如果在父类中定义属性而在子类中定义元模型来使用。这个属性就会有未被增强的风险。
		 * 
		 * 增加这样的检查逻辑，有利于用户在复杂继承关系下，确保父类的元模型不缺失，从而安全的使用。
		 * 
		 * 关于为什么不作增强父类的功能： a 父类可能在JAR包中，不能直接修改。 b
		 * 如果在子类中通过覆盖方法来实现，也有问题，因为ASM中去解析父类并查找同名方法较为复杂
		 * 。在增强前，不能调用类实现反射，因此相当于要自行用ASM实现父子类解析的JAVA逻辑，太麻烦了…… c
		 * 此外，如果父类本身也定义了该元模型
		 * ，子类覆盖父类元模型，此时也很悲剧——子类生成一个增强过的方法覆盖父类方法，而父类本身又做了增强
		 * ，此时延迟加载和等植入代码将被执行两遍。
		 * 因此，我们还是要尽可能避免这种父类定义属性，子类定义元模型的方式。即元模型要定义在各自的类里，子类可以覆盖父类的。
		 */
		boolean isEnhancedProperty = false;
		for (Field ff : fieldss) {
			Class<?> cc = ff.getClass().getDeclaringClass();
			if (cc == processingClz) {
				isEnhancedProperty = true;
				break;
			}
		}
		if (!isEnhancedProperty) {
			throw new IllegalArgumentException("Field [" + field.name() + "] may be not enhanced. Please add the enum Field [" + field.name() + "] into " + processingClz.getName());
		}
	}

	private static String getColumnName(Field field, Column a) {
		if (a != null && a.name().length() > 0) {
			return a.name().trim();
		} else {
			return field.name();
		}
	}

	private static JoinPath getHint(FieldAnnotationProvider annos, AbstractMetadata meta, ITableMetadata target) {
		if (annos.getAnnotation(JoinColumn.class) != null) {
			JoinColumn j = annos.getAnnotation(JoinColumn.class);
			return processJoin(meta, target, annos, j);
		} else if (annos.getAnnotation(JoinColumns.class) != null) {
			JoinColumns jj = annos.getAnnotation(JoinColumns.class);
			return processJoin(meta, target, annos, jj.value());
		} else if (annos.getAnnotation(JoinTable.class) != null) {
			JoinTable jt = annos.getAnnotation(JoinTable.class);
			return processJoin(meta, target, annos, jt);
		}
		return null;
	}

	private static JoinPath processJoin(AbstractMetadata meta, ITableMetadata target, FieldAnnotationProvider annos, JoinTable jt) {
		String table = jt.name();
		LogUtil.info("创建从{}到{}的关系。", meta.getName(), target.getName());
		// 计算生成关系表
		TupleMetadata rt = getDynamicMeta(table);
		JoinColumn[] jc1 = jt.joinColumns();
		String thisName = meta.getName().replace('.', '_');
		if (rt == null) {
			rt = new TupleMetadata(table);
			for (JoinColumn jc : jc1) {
				ColumnType ct = meta.getColumnType(jc.referencedColumnName());
				rt.addColumn(jc.name(), jc.name(), toNormal(ct), jt.uniqueConstraints().length > 0);
				rt.addIndex(jc.name(), null);
			}

			JoinColumn[] jc2 = jt.inverseJoinColumns();
			for (JoinColumn jc : jc2) {
				String name = jc.referencedColumnName();
				ColumnMapping ct = target.getColumnDef(target.getField(name));

				Assert.notNull(ct);
				rt.addColumn(jc.name(), jc.name(), toNormal(ct.get()), jt.uniqueConstraints().length > 0);
				rt.addIndex(jc.name(), null);
			}
			// 补充关系表注册
			putDynamicMeta(rt);
		}

		AbstractRefField refs = rt.getRefFieldsByName().get(thisName + "_OBJ");
		if (refs == null) {
			JoinColumn[] jc2 = jt.inverseJoinColumns();
			// 创建关系表到目标表的连接
			JoinPath path2 = processJoin(rt, target, annos, jc2);
			rt.addCascadeManyToOne(thisName + "_OBJ", target, path2);
		}
		refs = rt.getRefFieldsByName().get(thisName + "_OBJ");
		Assert.notNull(refs);

		// 创建到关系表的连接
		JoinColumn[] jc3 = new JoinColumn[jc1.length];
		for (int i = 0; i < jc1.length; i++) {
			JoinColumnImpl jc = new JoinColumnImpl(jc1[i]);
			jc.reverseColumn();
			jc3[i] = jc;
		}
		JoinPath path1 = processJoin(meta, rt, annos, jc3);
		path1.setRelationTable(rt, refs.getReference().getHint());
		return path1;
	}

	private static ColumnType toNormal(ColumnType columnType) {
		if (columnType instanceof AutoIncrement) {
			return ((AutoIncrement) columnType).toNormalType();
		} else if (columnType instanceof ColumnType.GUID) {
			return ((GUID) columnType).toNormalType();
		}
		return columnType;
	}

	protected static boolean processReference(AbstractMetadata meta, FieldAnnotationProvider field) {
		FieldOfTargetEntity targetField = field.getAnnotation(FieldOfTargetEntity.class);
		Cascade cascade = field.getAnnotation(Cascade.class);

		if (field.getAnnotation(OneToOne.class) != null) {
			OneToOne r1Vs1 = field.getAnnotation(OneToOne.class);
			ITableMetadata target = getTargetType(r1Vs1.targetEntity(), targetField, field, false);
			CascadeConfig config = new CascadeConfig(cascade, r1Vs1);
			config.path = getHint(field, meta, target);
			if (config.path == null) {
				String mappedBy = r1Vs1.mappedBy();
				if (StringUtils.isNotEmpty(mappedBy)) {
					config.path = processJoin(meta, target, field, mappedBy);
				}
			}
			if (targetField == null) {
				meta.addCascadeField(field.getName(), target, config);
			} else {
				jef.database.Field targetf = target.getField(targetField.value());
				meta.addCascadeField(field.getName(), targetf, config);
			}
			return true;
		}

		if (field.getAnnotation(OneToMany.class) != null) {
			OneToMany r1VsN = field.getAnnotation(OneToMany.class);
			ITableMetadata target = getTargetType(r1VsN.targetEntity(), targetField, field, true);
			CascadeConfig config = new CascadeConfig(cascade, r1VsN);
			config.path = getHint(field, meta, target);
			if (config.path == null) {
				String mappedBy = r1VsN.mappedBy();
				if (StringUtils.isNotEmpty(mappedBy)) {
					config.path = processJoin(meta, target, field, mappedBy);
				}
			}
			if (targetField == null) {
				meta.addCascadeField(field.getName(), target, config);
			} else {
				jef.database.Field targetf = target.getField(targetField.value());
				meta.addCascadeField(field.getName(), targetf, config);
			}
			return true;
		}

		if (field.getAnnotation(ManyToOne.class) != null) {
			ManyToOne rNVs1 = field.getAnnotation(ManyToOne.class);
			ITableMetadata target = getTargetType(rNVs1.targetEntity(), targetField, field, false);
			CascadeConfig config = new CascadeConfig(cascade, rNVs1);
			config.path = getHint(field, meta, target);
			if (targetField == null) {
				meta.addCascadeField(field.getName(), target, config);
			} else {
				jef.database.Field targetf = target.getField(targetField.value());
				meta.addCascadeField(field.getName(), targetf, config);
			}
			return true;
		}

		if (field.getAnnotation(ManyToMany.class) != null) {
			ManyToMany rNVsN = field.getAnnotation(ManyToMany.class);
			ITableMetadata target = getTargetType(rNVsN.targetEntity(), targetField, field, true);
			CascadeConfig config = new CascadeConfig(cascade, rNVsN);
			config.path = getHint(field, meta, target);
			if (config.path == null) {
				String mappedBy = rNVsN.mappedBy();
				if (StringUtils.isNotEmpty(mappedBy)) {
					config.path = processJoin(meta, target, field, mappedBy);
				}
			}
			if (targetField == null) {
				meta.addCascadeField(field.getName(), target, config);
			} else {
				jef.database.Field targetf = target.getField(targetField.value());
				meta.addCascadeField(field.getName(), targetf, config);
			}
			return true;
		}
		return false;
	}

	/**
	 * 计算出目标连接的类型
	 * 
	 * @param targetEntity
	 * @param targetField
	 * @param type
	 * @param isMany
	 *            是toMany类型的连�?
	 * @return
	 */
	private static ITableMetadata getTargetType(Class<?> targetEntity, FieldOfTargetEntity targetField, FieldAnnotationProvider field, boolean isMany) {
		if (targetEntity != void.class) {
			if (IQueryableEntity.class.isAssignableFrom(targetEntity)) {
				return MetaHolder.getMeta(targetEntity.asSubclass(IQueryableEntity.class));
			} else {
				throw new IllegalArgumentException("The target entity type [" + targetEntity.getName() + "] for " + field.getDeclaringClass().getSimpleName() + ":" + field.getName() + " is not subclass of DataObject.");
			}
		}
		if (targetField != null) {
			throw new IllegalArgumentException(field.getDeclaringClass().getSimpleName() + ":" + field.getName() + " miss its targetEntity annotation.");
		}
		if (isMany) {
			Class<?> compType = CollectionUtils.getSimpleComponentType(field.getGenericType());
			if (compType != null && IQueryableEntity.class.isAssignableFrom(compType)) {
				return MetaHolder.getMeta(compType.asSubclass(IQueryableEntity.class));
			}
		} else {
			Class<?> compType = field.getType();
			if (IQueryableEntity.class.isAssignableFrom(compType)) {
				return MetaHolder.getMeta(compType.asSubclass(IQueryableEntity.class));
			}
		}
		throw new IllegalArgumentException(field.getDeclaringClass().getSimpleName() + ":" + field.getName() + " miss its targetEntity annotation.");
	}

	private static JoinPath processJoin(AbstractMetadata thisMeta, ITableMetadata target, AnnotationProvider field, JoinColumn... jj) {
		List<JoinKey> result = new ArrayList<JoinKey>();

		String fieldName = field.getName();
		for (JoinColumn j : jj) {
			if (StringUtils.isBlank(j.name())) {
				throw new IllegalArgumentException("Invalid reference [" + thisMeta.getThisType().getName() + "." + fieldName + "]:The field 'name' in JoinColumn is empty");
			}
			Field left = thisMeta.getField(j.name());
			Assert.notNull(left, "Invalid reference [" + thisMeta.getThisType().getName() + "." + fieldName + "]: field [" + j.name() + "] not found from entity " + thisMeta.getThisType().getName());
			Field right = target.getField(j.referencedColumnName());
			if (right == null) {
				throw new NoResultException("Invalid reference [" + thisMeta.getThisType().getName() + "." + fieldName + "]: '" + j.referencedColumnName() + "' is not available in " + target.getThisType().getName());
			}
			result.add(new JoinKey(left, right));
		}
		JoinType type = JoinType.LEFT;
		JoinDescription joinDesc = field.getAnnotation(JoinDescription.class);
		if (joinDesc != null) {
			type = joinDesc.type();
		}
		if (result.size() > 0) {
			JoinPath path = new JoinPath(type, result.toArray(new JoinKey[result.size()]));
			path.setDescription(joinDesc, field.getAnnotation(OrderBy.class));
			if (joinDesc != null && joinDesc.filterCondition().length() > 0) {
				JoinKey joinExpress = getJoinExpress(thisMeta, target, joinDesc.filterCondition().trim());
				if (joinExpress != null)
					path.addJoinKey(joinExpress);
			}
			return path;
		}
		return null;
	}

	private static JoinPath processJoin(ITableMetadata meta, ITableMetadata target, AnnotationProvider annos, String mappedBy) {
		JoinDescription joinDesc = annos.getAnnotation(JoinDescription.class);
		OrderBy orderBy = annos.getAnnotation(OrderBy.class);

		List<JoinKey> result = new ArrayList<JoinKey>();

		if (meta.getPKFields().size() != 1) {
			throw new IllegalArgumentException(meta.getSimpleName() + " cann't map to " + target.getSimpleName() + " since its primary key field count " + meta.getPKFields().size());
		}
		Field left = meta.getPKFields().get(0).field();
		Field right = target.getField(mappedBy);
		if (right == null) {
			throw new IllegalArgumentException(meta.getSimpleName() + " cann't map to " + target.getSimpleName() + " since there is no field [" + mappedBy + "] in target entity");
		}
		result.add(new JoinKey(left, right));
		JoinType type = JoinType.LEFT;
		if (joinDesc != null) {
			type = joinDesc.type();
		}
		if (result.size() > 0) {
			JoinPath path = new JoinPath(type, result.toArray(new JoinKey[result.size()]));
			path.setDescription(joinDesc, orderBy);
			if (joinDesc != null && joinDesc.filterCondition().length() > 0) {
				JoinKey joinExpress = getJoinExpress(meta, target, joinDesc.filterCondition().trim());
				if (joinExpress != null)
					path.addJoinKey(joinExpress);
			}
			return path;
		}
		return null;
	}

	private static JoinKey getJoinExpress(ITableMetadata thisMeta, ITableMetadata targetMeta, String exp) {
		try {
			Expression ex = DbUtils.parseBinaryExpression(exp);
			if (ex instanceof BinaryExpression) {
				BinaryExpression bin = (BinaryExpression) ex;
				String left = bin.getLeftExpression().toString().trim();
				Field leftF = parseField(left, thisMeta, targetMeta);

				JoinKey key;
				if (leftF == null) {
					key = new JoinKey(null, null, new FBIField(bin, ReadOnlyQuery.getEmptyQuery(targetMeta)));// 建立一个函数Field
				} else {
					String oper = bin.getStringExpression();
					Expression right = bin.getRightExpression();
					Field rightF = null;
					if (right.getType() == ExpressionType.column || right.getType() == ExpressionType.function) {
						rightF = parseField(right.toString(), thisMeta, targetMeta);
					}
					if (rightF == null) {
						key = new JoinKey(leftF, Operator.valueOfKey(oper), new JpqlExpression(right.toString()));
					} else {
						key = new JoinKey(leftF, Operator.valueOfKey(oper), rightF);
					}

				}
				return key;
			} else {
				throw new RuntimeException("the expression " + exp + " is not a Binary Expression but a " + ex.getClass().getName());
			}
		} catch (ParseException e) {
			throw new RuntimeException("Unknown expression config on class:" + targetMeta.getThisType().getName() + ": " + exp);
		}
	}

	private static Field parseField(String keyword, ITableMetadata thisMeta, ITableMetadata target) {
		ITableMetadata meta = null;
		if (keyword.startsWith("this$")) {
			keyword = keyword.substring(5);
			meta = thisMeta;
		} else if (keyword.startsWith("that$")) {
			keyword = keyword.substring(5);
			meta = target;
		}
		if (meta != null) {
			ColumnMapping columnDef = meta.findField(keyword);// 假定左边的是字段
			if (columnDef != null) {
				return columnDef.field();
			} else {
				FBIField field = new FBIField(keyword, ReadOnlyQuery.getEmptyQuery(meta));
				field.setBindBase(true);
				;
				return field;
			}
		} else {
			ColumnMapping columnDef = target.findField(keyword);
			if (columnDef != null) {
				return columnDef.field();
			}
			columnDef = thisMeta.findField(keyword);
			if (columnDef != null) {
				return columnDef.field();
			}
			return null;
		}
	}


	/**
	 * 逆向查找元模型
	 * 
	 * @param schema
	 * @param table
	 */
	public static AbstractMetadata lookup(String schema, String table) {
		String key = (schema + "." + table).toUpperCase();
		AbstractMetadata m = inverseMapping.get(key);
		if (m != null)
			return m;

		// Schema还原
		if (schema != null) {
			schema = schema.toUpperCase();
			for (Entry<String, String> e : SCHEMA_MAPPING.entrySet()) {
				if (e.getValue().equals(schema)) {
					schema = e.getKey();
					break;
				}
			}
		}

		// Lookup static models
		for (AbstractMetadata meta : pool.values()) {
			String tablename = meta.getTableName(false);
			if (schema != null && (!StringUtils.equals(meta.getSchema(), schema))) {// schema不同则跳
				continue;
			}
			if (tablename.equalsIgnoreCase(table)) {
				m = meta;
				break;
			}
		}
		if (m == null) {
			// Lookup dynamic models
			for (AbstractMetadata meta : dynPool.values()) {
				String tablename = meta.getTableName(false);
				if (schema != null && (!StringUtils.equals(meta.getSchema(), schema))) {// schema不同则跳
					continue;
				}
				if (tablename.equalsIgnoreCase(table)) {
					m = meta;
					break;
				}
			}
		}
		inverseMapping.put(key, m);
		return m;
	}

	public static void clear() {
		pool.clear();
		dynPool.clear();
		inverseMapping.clear();
	}
}
