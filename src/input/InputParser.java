package input;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

	public static Map<ResourceType, ResourceOwner> parseProviderPerResource(String file) {
		Map<ResourceType, ResourceOwner> providerPerResource = new HashMap<>();
			try {
				for(String s: Files.readAllLines(Paths.get(file)))
				{
					String[] split = s.split(";");
					ResourceOwner rp = ResourceOwner.newInstance(split[0]);
					for(int i = 1 ; i < split.length; i++)
						providerPerResource.put(
								ResourceType.newInstance(split[i]),rp);
				}
			} catch (IOException e) {
				e.printStackTrace();
				throw new Error();
			}
		return providerPerResource;
	}

	public static Map<ResourceType, Integer> parseAmountPerResource(String file) {
		Map<ResourceType, Integer> res = new HashMap<>();

		try {
			for(String s: Files.readAllLines(Paths.get(file)))
			{
				String[] split = s.split(";");
				res.put(ResourceType.newInstance(split[0]), Integer.parseInt(split[1]));
			}
		} catch (IOException e) {
			e.printStackTrace();
			throw new Error();
		}
		return res;
	}

	public static UserPreferenceMeaning parseUserPreferenceMeaning(String string) {
		return UserPreferenceMeaning.valueOf(string);
	}

}
