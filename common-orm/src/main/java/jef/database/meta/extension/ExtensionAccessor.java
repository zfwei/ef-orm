package jef.database.meta.extension;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import jef.accelerator.bean.BeanAccessor;
import jef.database.meta.ExtensionConfigFactory;
import jef.database.meta.ExtensionTemplate;
import jef.tools.reflect.FieldAccessor;
import jef.tools.reflect.Property;

public final class ExtensionAccessor extends BeanAccessor implements ExtensionModificationListener {
	private BeanAccessor accessor;
	/**
	 * 扩展属性列表
	 */
	private Map<String, Property> extProperties;
	/**
	 * 混合后的属性列表
	 */
	private Collection<String> allPropNames;

	private Collection<Property> allProps;

	private ReadWriteLock rwLock = new ReentrantReadWriteLock();

	private FieldAccessor key;
	
	private String extensionName;
	/**
	 * 构造
	 * 
	 * @param raw
	 * @param provider
	 */
	public ExtensionAccessor(BeanAccessor raw, String extensionName, BeanExtensionProvider provider) {
		this.accessor = raw;
		this.extensionName=extensionName;
		setExtProperties(provider.getExtensionProperties(raw.getType(), extensionName, this));
		ExtensionConfigFactory ef=provider.getExtensionFactory(raw.getType());
		if(ef instanceof ExtensionTemplate){
			this.key=((ExtensionTemplate) ef).getKeyAccessor();
		}
		
	}

	/**
	 * 当元数据发生变化时
	 * 
	 * @param extProps
	 */
	public void setExtProperties(Map<String, Property> extProps) {
		Lock lock = rwLock.writeLock();
		lock.lock();
		try {
			Collection<String> rawNames = accessor.getPropertyNames();
			Collection<? extends Property> rawProperties = accessor.getProperties();
			List<String> mergeNames = new ArrayList<String>(rawNames.size() + extProps.size());
			List<Property> mergeProperties = new ArrayList<Property>(rawNames.size() + extProps.size());
			mergeNames.addAll(rawNames);
			mergeNames.addAll(extProps.keySet());
			mergeProperties.addAll(rawProperties);
			mergeProperties.addAll(extProps.values());
			this.extProperties = extProps;
			this.allPropNames = mergeNames;
			this.allProps = mergeProperties;
		} finally {
			lock.unlock();
		}

	}

	@Override
	public Collection<String> getPropertyNames() {
		Lock lock = rwLock.readLock();
		lock.lock();
		try {
			return allPropNames;
		} finally {
			lock.unlock();
		}
	}

	@Override
	public Class<?> getPropertyType(String name) {
		Lock lock = rwLock.readLock();
		lock.lock();
		Property pp;
		try {
			pp = extProperties.get(name);
		} finally {
			lock.unlock();
		}
		return pp == null ? accessor.getPropertyType(name) : pp.getType();
	}

	@Override
	public Type getGenericType(String name) {
		Lock lock = rwLock.readLock();
		lock.lock();
		try {
			Property pp = extProperties.get(name);
			return pp == null ? accessor.getGenericType(name) : pp.getGenericType();
		} finally {
			lock.unlock();
		}
	}

	@Override
	public Object getProperty(Object bean, String name) {
		Lock lock = rwLock.readLock();
		lock.lock();
		try {
			Property pp = extProperties.get(name);
			return pp == null ? accessor.getProperty(bean, name) : pp.get(bean);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public boolean setProperty(Object bean, String name, Object v) {
		Lock lock = rwLock.readLock();
		lock.lock();
		try {
			Property pp = extProperties.get(name);
			if (pp == null) {
				return accessor.setProperty(bean, name, v);
			} else {
				pp.set(bean, v);
				return true;
			}
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void copy(Object o1, Object o2) {
		accessor.copy(o1, o2);
		Lock lock = rwLock.readLock();
		lock.lock();
		try {
			for (Property pp : extProperties.values()) {
				pp.set(o2, pp.get(o1));
			}
		} finally {
			lock.unlock();
		}
	}

	@Override
	public Property getProperty(String name) {
		Lock lock = rwLock.readLock();
		lock.lock();
		try {
			Property pp = extProperties.get(name);
			return pp == null ? accessor.getProperty(name) : pp;
		} finally {
			lock.unlock();
		}
	}

	@Override
	public Collection<? extends Property> getProperties() {
		return allProps;
	}

	@Override
	public Map<Class<?>, Annotation> getAnnotationOnField(String name) {
		Property pp = extProperties.get(name);
		if(pp==null){
			return accessor.getAnnotationOnField(name);
		}else{
			return null;
		}
	}

	@Override
	public Map<Class<?>, Annotation> getAnnotationOnGetter(String name) {
		return accessor.getAnnotationOnGetter(name);
	}

	@Override
	public Map<Class<?>, Annotation> getAnnotationOnSetter(String name) {
		return accessor.getAnnotationOnSetter(name);
	}

	@Override
	public Object newInstance() {
		Object obj=accessor.newInstance();
		if(key!=null){
			key.set(obj, extensionName);
		}
		return obj;
	}

	@Override
	public Class<?> getType() {
		return accessor.getType();
	}
	
	@Override
	public void initAnnotations(Map<Class<?>,Annotation>[] field, Map<Class<?>,Annotation>[] getter, Map<Class<?>,Annotation>[] setter) {
		accessor.initAnnotations(field, getter, setter);
	}

	@Override
	public void initNthGenericType(int index, Class<?> raw, Type type, int total, String fieldName) {
		accessor.initNthGenericType(index, raw, type, total, fieldName);
	}

	@Override
	public Map<String, Object> convert(Object obj) {
		Map<String,Object> map=accessor.convert(obj);
		for(Map.Entry<String, Property> entry:extProperties.entrySet()) {
			map.put(entry.getKey(), entry.getValue().get(obj));
		}
		return map;
	}

	@Override
	public Object fromMap(Map<String, Object> map) {
		Object o=accessor.fromMap(map);
		for(Map.Entry<String, Property> entry:extProperties.entrySet()) {
			entry.getValue().set(o, map.get(entry.getKey()));
		}
		return o;
	}

	@Override
	public Object fromMap2(Map<String, Object> map) {
		Object o=accessor.fromMap2(map);
		for(Map.Entry<String, Property> entry:extProperties.entrySet()) {
			entry.getValue().set(o, map.get(entry.getKey()));
		}
		return o;
	}
}
