package input;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import input.InputBuilder.ParameterTypes;
import model.ResourceType;
import model.ResourceOwner;
import model.User;
import model.UserResourceTypeAllocation;
import model.UserGroup;

public class InputParser {

	public static Map<UserResourceTypeAllocation, Double> parseUserPreference(String file) {
		Map<UserResourceTypeAllocation, Double>absoluteValuePerAllocation = new HashMap<>();
		try {
			for(String s: Files.readAllLines(Paths.get(file)))
			{
				String[] split = s.split(";");
				
				List<String> userNames = new ArrayList<>();
				userNames.addAll(Arrays.asList(split));
				userNames.remove(0);
				userNames.remove(userNames.size()-1);
				
				UserGroup usergroup = 
						UserGroup.newInstance(
						userNames
						.stream()
						.map(x-> User.newInstance(x))
						.collect(Collectors.toSet()));
	
				Double associatedValue = 
						Double.parseDouble(split[split.length-1]);
				
				
				for(User u: usergroup.getUsers())
				{
					UserResourceTypeAllocation ua = UserResourceTypeAllocation.newInstance(u, 
							ResourceType.newInstance(split[0]));
	
					if(absoluteValuePerAllocation.containsKey(ua))
						throw new IllegalArgumentException(
								"Each allocation should be associated a score once, but "
										+ "the allocation:"+ua+" is assigned a score at least twice!");
					absoluteValuePerAllocation.put(ua,associatedValue);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			throw new Error();
		}
		
		return absoluteValuePerAllocation;
	}

	public static Set<UserGroup> parseUserGroups(String file) {
		Map<User, UserGroup> groupsPerUsers = new HashMap<>();

		Map<UserResourceTypeAllocation, Double>absoluteValuePerAllocation = new HashMap<>();
		try {
			for(String s: Files.readAllLines(Paths.get(file)))
			{
				String[] split = s.split(";");
				
				List<String> userNames = new ArrayList<>();
				userNames.addAll(Arrays.asList(split));
				userNames.remove(0);
				userNames.remove(userNames.size()-1);
				
				UserGroup usergroup = 
						UserGroup.newInstance(
						userNames
						.stream()
						.map(x-> User.newInstance(x))
						.collect(Collectors.toSet()));
				checkForInconsistencies(groupsPerUsers, usergroup);
				
				for(User u: usergroup.getUsers())
					groupsPerUsers.put(u, usergroup);
	
				Double associatedValue = 
						Double.parseDouble(split[split.length-1]);
				
				
				for(User u: usergroup.getUsers())
				{
					UserResourceTypeAllocation ua = UserResourceTypeAllocation.newInstance(u, 
							ResourceType.newInstance(split[0]));
	
					if(absoluteValuePerAllocation.containsKey(ua))
						throw new IllegalArgumentException(
								"Each allocation should be associated a score once, but "
										+ "the allocation:"+ua+" is assigned a score at least twice!");
					absoluteValuePerAllocation.put(ua,associatedValue);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			throw new Error();
		}
		
		return groupsPerUsers.values().stream().collect(Collectors.toSet());
	}
	
	private static void checkForInconsistencies(
			Map<User, UserGroup> groupsPerUsers,
			UserGroup usergroup) {
		boolean isPresent = groupsPerUsers
				.containsKey(usergroup.getUsers().iterator().next());
		
		if(isPresent)
		{
			for(User u: usergroup.getUsers())
				if(!groupsPerUsers.get(u).equals(usergroup))
					throw new Error(
							"User:"+u+" belongs to at least two groups: "+
					usergroup+" and "+groupsPerUsers.get(u));
		}
		else
			for(User u: usergroup.getUsers())
				if(groupsPerUsers.containsKey(u))
					throw new Error(
							"User:"+u+" belongs to at least two groups: "+
					usergroup+" and "+groupsPerUsers.get(u));
			
	}

	public static Function<ResourceType, ResourceOwner> parseProviderPerResource(String input) {
		if(input.equals(ResourceOwnershipMode.Enum.DISABLED.toString()))
			return x->ResourceOwner.newInstance("everybody");
			
		if(input.startsWith(ResourceOwnershipMode.Enum.FILE_BASED.toString()))
		{
			String fileName = input.substring(input.indexOf("(")+1,input.indexOf(","));
			input = input.substring(input.indexOf(",")+1);
			int resourceCol = Integer.parseInt(input.substring(0,input.indexOf(",")));
			input = input.substring(input.indexOf(",")+1);
			int ownerCol = Integer.parseInt(input.substring(0, input.length()-1));
			
		  Map<ResourceType, ResourceOwner> providerPerResource = new HashMap<>();
			try {
				for(String s: Files.readAllLines(Paths.get(fileName)))
				{
					String[] split = s.split(";");
					ResourceOwner rp = ResourceOwner.newInstance(split[ownerCol]);
					for(int i = 1 ; i < split.length; i++)
						providerPerResource.put(
								ResourceType.newInstance(split[resourceCol]),rp);
				}
			} catch (IOException e) {
				e.printStackTrace();
				throw new Error();
			}
		return x->
		{
			return providerPerResource.get(x);
		};
		}
			throw new Error();
	}


	public static Function<ResourceType, Integer> parseAmountPerResource(String value) {

		if(value.equals("ONE_OF_EACH"))
			return x->1;
			else if(value.startsWith("FILE_BASED"))
			{
				String fileName = value.substring(value.indexOf("(")+1, value.indexOf(","));
				value = value.substring(value.indexOf(",")+1);
				int resourceNameColumn = Integer.parseInt(value.substring(0, value.indexOf(",")));
				int resourceAmountColumn = Integer.parseInt(value.substring(value.indexOf(",")+1,value.indexOf(")")));
				Map<ResourceType, Integer> res = new HashMap<>();

				try {
					for(String s: Files.readAllLines(Paths.get(fileName)))
					{
						String[] split = s.split(";");
						
						String resourceNumberS = split[resourceAmountColumn];
						try {
							Integer.parseInt(resourceNumberS);
						}
						catch(NumberFormatException e)
						{
							System.err.println("Error when parsing the file describing the amount of instance per resource");
							System.err.println("The column indicating the instance number is:"+resourceAmountColumn);
							System.err.println("The line read is: "+s);
							System.err.println("The column #"+resourceAmountColumn+" is not a number: "+resourceNumberS);
							System.err.println("Info: The file is located in:"+fileName);
							throw new Error();
							
						}
						res.put(ResourceType.newInstance(split[resourceNameColumn]), 
								Integer.parseInt(resourceNumberS));
					}
				} catch (IOException e) {
					e.printStackTrace();
					throw new Error();
				}
				return x->res.get(x);
			}
			else throw new Error();
	}

	public static UserPreferenceMeaning parseUserPreferenceMeaning(String string) {
		return UserPreferenceMeaning.valueOf(string);
	}

}
