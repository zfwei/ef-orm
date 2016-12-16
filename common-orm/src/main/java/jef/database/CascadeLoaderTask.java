package jef.database;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import jef.common.log.LogUtil;
import jef.database.annotation.JoinDescription;
import jef.database.dialect.type.ColumnMapping;
import jef.database.jsqlparser.statement.select.OrderBy;
import jef.database.jsqlparser.statement.select.OrderByElement;
import jef.database.meta.AbstractRefField;
import jef.database.meta.ISelectProvider;
import jef.database.meta.ITableMetadata;
import jef.database.meta.JoinPath;
import jef.database.meta.Reference;
import jef.database.meta.ReferenceField;
import jef.database.query.JoinElement;
import jef.database.query.OrderField;
import jef.database.query.Query;
import jef.database.query.QueryBuilder;
import jef.tools.StringUtils;
import jef.tools.reflect.BeanWrapper;

final class CascadeLoaderTask implements LazyLoadTask {
	private ReverseReferenceProcessor reverse;
	private Query<?> query;
	private JoinElement finalQuery;
	private QueryOption option;
	private JoinPath joinPath;
	private Map<Reference, List<Condition>> filters;
	private List<Condition> currentFilter;
	private ITableMetadata targetTableMeta;
	private List<AbstractRefField> refs;
	private List<OrderField> orders;
	private String keyOfJoinTable;
	/**
	 * 
	 * @param entry
	 *            模型中的静态条件
	 * @param filters
	 *            人工的动态过滤条件
	 */
	public CascadeLoaderTask(Map.Entry<Reference, List<AbstractRefField>> entry,  Map<Reference, List<Condition>> filters) {
		this.filters = filters;
		this.refs = entry.getValue(); // 要装填的字段

		Reference ref = entry.getKey(); // 引用关系
		this.currentFilter=filters.get(ref);
		joinPath = ref.toJoinPath();
		if (joinPath == null) {
			LogUtil.error("No join key found: " + ref);
		}
		this.reverse = CascadeUtil.getReverseProcessor(ref);
		
		if(joinPath.getRelationTable()!=null) {
			query = QueryBuilder.create(joinPath.getRelationTable());
			keyOfJoinTable=ref.getThisType().getName().replace('.', '_')+"_OBJ";
		}else {
			query = QueryBuilder.create(ref.getTargetType());	
		}
		
		
		targetTableMeta = query.getMeta();

		// 将预制的两个条件加入
		if (joinPath.getDescription() != null) {
			JoinDescription desc = joinPath.getDescription();
			if (desc.maxRows() > 0) {
				query.setMaxResult(desc.maxRows());
			}
		}
		if (StringUtils.isNotEmpty(joinPath.getOrderBy())) {
			orders = new ArrayList<OrderField>();
			OrderBy order = DbUtils.parseOrderBy(joinPath.getOrderBy());
			for (OrderByElement ele : order.getOrderByElements()) {
				ColumnMapping field = targetTableMeta.findField(ele.getExpression().toString());
				if (field != null) {
					orders.add(new OrderField(field.field(), ele.isAsc()));
				}
			}
		}
		
		// 计算查询目标的引用关系
		finalQuery = query;
		option = QueryOption.createFrom(query);
		if(reverse!=null)
			option.skipReference=reverse.refs;
		if (!targetTableMeta.getRefFieldsByName().isEmpty()) {
			finalQuery = DbUtils.toReferenceJoinQuery(query, reverse == null ? null : reverse.refs); // 去除引用关系后将其转为Join，用这种方式进行的查询不查询反向的多对1关系
		}
	}

	public void process(Session db, Object obj) throws SQLException {
		if(!db.isOpen()){
			throw new SQLException("try to load field "+refs.get(0).getName()+" but the session was already closed!");
		}
		LogUtil.debug("processing Cascadeload [{}]",this.refs.get(0).getReference());
		BeanWrapper bean = BeanWrapper.wrap(obj);
		if (DbUtils.appendRefCondition(bean, joinPath, query, currentFilter) == false)
			return;
		if(orders!=null){
			for(OrderField f:orders){
				query.addOrderBy(f.isAsc(), f.getField());
			}
		}
		String isManyToMany=this.keyOfJoinTable;
		@SuppressWarnings("unchecked")
		List<IQueryableEntity> subs = db.innerSelect(finalQuery, null, filters, option);		
		if(isManyToMany!=null) {
			List<? extends IQueryableEntity> old=subs;
			subs=new ArrayList<IQueryableEntity>();
			for(IQueryableEntity d: old) {
				IQueryableEntity realObj=(IQueryableEntity) ((VarObject)d).get(isManyToMany);
				if(realObj==null) {
					LogUtil.warn("Missing right record connect to {}, where {}",this.targetTableMeta.getTableName(false),query);
				}else {
					subs.add(realObj);
				}
			}
		}
		
		for (ISelectProvider reff : refs) { // 根据配置装填到对象中去
			AbstractRefField refield = (AbstractRefField) reff;

			Class<?> container = refield.getSourceFieldType();
			if (refield.isSingleColumn()) {// 引用字段填充
				Object value = SqlProcessor.collectValueToContainer(subs, container, ((ReferenceField) refield).getTargetField().fieldName());
				refield.getField().set(obj, value);
			} else { // 全引用填充
				Object value;
				if (targetTableMeta.getContainerType() == container) {
					value = subs.isEmpty() ? null : subs.get(0);
				} else {
					value = DbUtils.toProperContainerType(subs, container,targetTableMeta.getContainerType(),refield);
				}
				refield.getField().set(obj, value);
			}
		}
		// 反相关系
		if (reverse != null) {
			reverse.process(bean.getWrapped(), subs);
		}
	}

	public Collection<String> getEffectFields() {
		String[] str = new String[refs.size()];
		for (int i = 0; i < refs.size(); i++) {
			ISelectProvider p = refs.get(i);
			str[i] = p.getName();
		}
		return Arrays.asList(str);
	}
}
