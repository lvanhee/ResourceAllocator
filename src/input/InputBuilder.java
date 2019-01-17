package input;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import model.ResourceType;
import model.ResourceOwner;
import model.User;
import model.UserResourceTypeAllocation;
import model.UserGroup;

public class InputBuilder {
	
	public enum ParameterTypes{
		PREFERENCE_FILE,
		MIN_NB_USER_PER_RESOURCE,
		MAX_NB_USER_PER_RESOURCE,
		RESOURCE_PER_OWNER,
		AMOUNT_PER_RESOURCE,
		PREFERENCE_MEANING
	}

	private final Map<ParameterTypes, String> valuePerInputType;
	
	private InputBuilder(Map<ParameterTypes, String> valuePerInputType) {
		this.valuePerInputType = valuePerInputType;
	}

	public static InputBuilder parse(String[] args) {
		Map<ParameterTypes, String> valuePerInputType = new HashMap<>(); 
		for(String s: args)
		{
			valuePerInputType.put(ParameterTypes.valueOf(s.split(":")[0]),s.split(":")[1]);
		}
		InputBuilder ib =new InputBuilder(valuePerInputType);
		checkCoherence(ib);
		return ib;
	}

	public String get(ParameterTypes it) {
		return valuePerInputType.get(it);
	}
	
	public static void checkCoherence(InputBuilder args) {
		
	}

}
