package jef.tools.reflect;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import jef.accelerator.bean.BeanAccessor;
import jef.accelerator.bean.FastBeanWrapperImpl;

@SuppressWarnings("rawtypes")
public final class BeanAccessorMapImpl extends BeanAccessor {
	public static final BeanAccessorMapImpl INSTANCE = new BeanAccessorMapImpl();

	private BeanAccessor pojoAccessor;
	
	private BeanAccessorMapImpl() {
	}
	
	public BeanAccessorMapImpl(Class<?> pojo) {
		this.pojoAccessor=FastBeanWrapperImpl.getAccessorFor(pojo);
	}

	public Collection<String> getPropertyNames() {
		return pojoAccessor==null?Collections.<String>emptyList():pojoAccessor.getPropertyNames();
	}

	public Class<?> getPropertyType(String name) {
		return pojoAccessor==null?Object.class:pojoAccessor.getPropertyType(name);
	}

	public Type getGenericType(String name) {
		return pojoAccessor==null?Object.class:pojoAccessor.getGenericType(name);
	}

	public Object getProperty(Object bean, String name) {
		return ((Map) bean).get(name);
	}

	@SuppressWarnings("unchecked")
	public boolean setProperty(Object bean, String name, Object v) {
		((Map) bean).put(name, v);
		return true;
	}

	@SuppressWarnings("unchecked")
	public void copy(Object o1, Object o2) {
		Map m1 = (Map) o1;
		Map m2 = (Map) o2;
		m2.clear();
		m2.putAll(m1);
	}

	public Property getProperty(String name) {
		return new MapProperty(name);
	}

	public Collection<? extends Property> getProperties() {
		return pojoAccessor==null?Collections.<Property>emptyList():pojoAccessor.getProperties();
	}

	public Map<Class<?>, Annotation> getAnnotationOnField(String name) {
		return pojoAccessor==null?null:pojoAccessor.getAnnotationOnField(name);
	}

	public Map<Class<?>, Annotation> getAnnotationOnGetter(String name) {
		return pojoAccessor==null?null:pojoAccessor.getAnnotationOnGetter(name);
	}

	public Map<Class<?>, Annotation> getAnnotationOnSetter(String name) {
		return pojoAccessor==null?null:pojoAccessor.getAnnotationOnSetter(name);
	}

	public void initAnnotations(Map<Class<?>, Annotation>[] field,
			Map<Class<?>, Annotation>[] getter,
			Map<Class<?>, Annotation>[] setter) {
	}

	public void initNthGenericType(int index, Class<?> raw, Type type,
			int total, String fieldName) {
	}

	@Override
	public Object newInstance() {
		return new HashMap();
	}

	@Override
	public Class<?> getType() {
		return Map.class;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Map<String, Object> convert(Object obj) {
		return (Map) obj;
	}

	@Override
	public Object fromMap(Map<String, Object> map) {
		return map;
	}

	@Override
	public Object fromMap2(Map<String, Object> map) {
		return map;
	}

	final class MapProperty implements Property {
		private String name;

		public String getName() {
			return name;
		}

		MapProperty(String name) {
			this.name = name;
		}

		public boolean isReadable() {
			return pojoAccessor==null?true:pojoAccessor.getProperty(name)!=null;
		}

		public boolean isWriteable() {
			return pojoAccessor==null?true:pojoAccessor.getProperty(name)!=null;
		}

		public Object get(Object obj) {
			return ((Map) obj).get(name);
		}

		@SuppressWarnings({ "unchecked" })
		public void set(Object obj, Object value) {
			((Map) obj).put(name, value);
		}

		public Class<?> getType() {
			return pojoAccessor==null?Object.class:pojoAccessor.getPropertyType(name);
		}

		public Type getGenericType() {
			return pojoAccessor==null?Object.class:pojoAccessor.getGenericType(name);
		}
	}
}

