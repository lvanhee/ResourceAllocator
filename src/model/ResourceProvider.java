package model;

import java.util.Set;

public class ResourceProvider {

	private final String name;
	private ResourceProvider(String string) {
		this.name = string;
	}

	public static ResourceProvider newInstance(String string) {
		return new ResourceProvider(string);
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
		return ((ResourceProvider)o).name.equals(name);
	}

}
