package com.github.geequery.springdata.test.entity;

import java.util.Date;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Version;

@Entity()
public class VersionLog extends jef.database.DataObject {
	private static final long serialVersionUID = 1L;

	@Id
    @GeneratedValue
    private int id;

    private String name;
    
    @GeneratedValue(generator = "modified-sys")
    private Date modified;
    
    @Version
    private int version;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

	public Date getModified() {
		return modified;
	}

	public void setModified(Date modified) {
		this.modified = modified;
	}

	public int getVersion() {
		return version;
	}

	public void setVersion(int version) {
		this.version = version;
	}


	public enum Field implements jef.database.Field {
        id, name, modified, version
    }
}
