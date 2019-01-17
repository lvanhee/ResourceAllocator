package model;

import java.util.Set;

public class ResourceOwner {

	private final String name;
	private ResourceOwner(String string) {
		this.name = string;
	}

	public static ResourceOwner newInstance(String string) {
		return new ResourceOwner(string);
	}
	
	public String toString()
	{
		return name;
	}

	public String getName() {
		return name;
	}
	
	public int hashCode()
	{
		return name.hashCode();
	}
	
	public boolean equals(Object o)
	{
		return ((ResourceOwner)o).name.equals(name);
	}

}
