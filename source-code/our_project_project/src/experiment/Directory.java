package experiment;

import java.util.ArrayList;

public class Directory {
	
	public String name;
	
	public ArrayList<Directory> children = null;
	
	public Directory(String name) {
		this.name = name;
		children = new ArrayList<Directory>();
	}
	
	public void add(Directory d) {
		children.add(d);
	
	}
	
<<<<<<< HEAD
	public ArrayList<Directory> getChildren() {
		return children;
	}
=======
<<<<<<< HEAD
	public Directory get(int index) {
		return children.get(index);
	}
=======
>>>>>>> 3106b4a6c49183e0ccc141b0819923a3e7ad1b87
	
	
	public Directory get(int index) {
		return children.get(index);
	}
	
	public Directory get(String indexName) {
		int index = children.indexOf(indexName);
		return get(index);
	}
>>>>>>> 11445146f01a993578c92521c1320761a395aa79

}
  