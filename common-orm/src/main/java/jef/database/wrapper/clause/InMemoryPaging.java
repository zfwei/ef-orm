package jef.database.wrapper.clause;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import jef.database.jdbc.rowset.CachedRowSetImpl;
import jef.database.jdbc.rowset.Row;
import jef.tools.PageLimit;

/**
 * 在内存中实现结果集分页
 * @author jiyi
 *
 */
public class InMemoryPaging{
	private int start;
	private int limit;
	
	public InMemoryPaging(int start,int limit){
		this.start=start;
		this.limit=limit;
	}
	
	public InMemoryPaging(PageLimit range) {
		int[] data=range.toArray();
		this.start=data[0];
		this.limit=data[1];
	}

	public void process(CachedRowSetImpl rows) throws SQLException {
		List<Row> list=rows.getRvh();

		int end=start+limit;
		if(start==0 && end>=list.size()){//不需要截取的场合
			return;
		}
		if(end>list.size()){ //防止溢出
			end=list.size();
		}
		if(end<=start || start>=rows.size()){//防止空结果
			rows.setRvh(new ArrayList<Row>());
		}else{
			rows.setRvh(list.subList(start, end));
		}
		rows.refresh();	
	}

	public String getName() {
		return "PAGING";
	}
	public int getOffset(){
		return start;
	}
	
	public int getLimit(){
		return limit;
	}
	
	
}
