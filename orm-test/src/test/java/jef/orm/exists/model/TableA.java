package jef.orm.exists.model;

import javax.persistence.Entity;

@Entity()
public class TableA extends jef.database.DataObject {
    private Integer id;

    public TableA(){};
    public TableA(int id){
    	this.id=id;
    }
    
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public enum Field implements jef.database.Field {
        id
    }
}
