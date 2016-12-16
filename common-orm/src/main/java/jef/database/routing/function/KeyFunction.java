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
package jef.database.routing.function;


/**
 * 描述一个对指定类型的函数操作，用来将不同的值转化为分表时的表名后缀
 * @author Administrator
 *
 */
public enum KeyFunction {
	
	/**
	 * 对文本取Hash值，然后按1024取模。按照余数的分布来选择到不同的分区上。<p>
	 * 对应类为：jef.database.routing.function.HashMod1024MappingFunction.HashMod1024MappingFunction<br>
	 * <h3>算法机制</h3>
	 * 将文本截断后，计算其HashCode并按1024取模。从而得到一个范围为0~1023的整数。
	 * 然后根据实现划定的范围，将这个整数映射到一个表名/库名上。
	 * 
	 * <h3>配置方法一</h3>
	 * 支持properties参数:<br>
	 * <code>
	 * 	partition.bucket.range = 0:DB1,256:DB2,512:DB3,768:DB4
	 * </code><br>
	 * 上述配置等效于<br>
	 * <code>
	 * 	partition.bucket.range = 0-255:DB1,256-511:DB2,512-767:DB3,768-1023:DB4
	 * </code>
	 * 上例中，可以控制将0~1023的小片分别平均分到四个表/库中。
	 * 
	 * <h3>配置方法2</h3>
	 * 使用Annotation中的functionConstructorParams.
	 * <code><pre>@PartitionKey(
	 *		field="field",
	 *		function=KeyFunction.HASH_MOD1024_RANGE,
	 *		functionConstructorParams= {"0:DB1,256:DB2,512:DB3,768:DB4","10"}
	 *	) 	
	 * </pre></code>
	 * 使用注解也可以指定该对象的分区范围，第二个参数“10”还可以指定计算hashCode的文本长度。
	 */
	HASH_MOD1024_RANGE,
	
	/**
	 * 10求余。参数由functionConstructorParams确定
	 */
	MODULUS, //取余
	
	/**
	 * 对指定的日期型字段，获取yyyy格式年份
	 */
	YEAR,  //年
	/**
	 * 对指定的日期型字段，获取yyyyMM格式年月
	 */
	YEAR_MONTH,//年+月
	/**
	 * 对指定的日期型字段，获取yyyyMMdd格式年月
	 */
	YEAR_MONTH_DAY,//年月日  yyyyMMdd
	/**
	 * 对指定的日期型字段，获取MM格式月份
	 */
	MONTH,  //月
	/**
	 * 对指定的日期型字段，获取dd格式日
	 */
	DAY,    //日
	/**
	 * 对指定日期类型字段，取年的最后两位
	 */
	YEAR_LAST2,
	/**
	 * 对指定日期类型字段，取小时数字(24)
	 */
	HH24,//24小时的小时数
	/**
	 * 对指定日期类型字段，取星期(0表示星期日，1~6)
	 */
	WEEKDAY,
	/**
	 * 对任意类型的字段，将其数值转换为String<p>
	 * <b>默认值</b>
	 */
	RAW,//不处理
	/**
	 * 常量映射，如启动参数配置为如下。
	 * {@code "M:1,F:2,01-20:3,21-41:4,A-Z:12"}
	 * 表示当值为M时，映射为1，01~20之间映射为3，21-41之间映射为4。 以此类推 
	 * 
	 */
	MAPPING,
}
