package org.telcomp.sbb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.slee.ActivityContextInterface;
import javax.slee.RolledBackContext;
import javax.slee.SbbContext;
import javax.slee.serviceactivity.ServiceActivity;
import javax.slee.serviceactivity.ServiceActivityFactory;
import javax.slee.serviceactivity.ServiceStartedEvent;

import servicecategory.Input;
import servicecategory.Operation;
import servicecategory.Output;

import com.google.code.morphia.Datastore;
import com.google.code.morphia.Morphia;
import com.mongodb.Mongo;

import datamodel.PetriNet;
import datamodel.Place;
import datamodel.Token;

public abstract class ReconfigurationSbb implements javax.slee.Sbb {
	
	private ServiceActivityFactory saf;

	public void onServiceStartedEvent (ServiceStartedEvent  event, ActivityContextInterface aci) {
		ServiceActivity sa = saf.getActivity();
		if(sa.equals(aci.getActivity())){
			aci.detach(this.sbbContext.getSbbLocalObject());
			
			//Simulating Input obtained from Monitoring Service
			HashMap<String, String> reconfigInputs = new HashMap<String, String>();
			reconfigInputs.put("ServiceName", "LinkedInJobNotificator");
			reconfigInputs.put("operationName", "sendTwitterMessage");
			reconfigInputs.put("mainControlFlow", "4");
			reconfigInputs.put("branchControlFlow1", "2");
			reconfigInputs.put("branchControlFlow2", "2");
			
			//Reconfiguration Parameters
			String operationName;
			ArrayList<Place> IOPlaces = new ArrayList<Place>();
			Place reconfigInputPlace;
			Place reconfigOutputPlace;
			Operation reconfigOperation;
			List<Operation> candidateOperations = new ArrayList<Operation>();
			boolean outputsCheck;
			boolean inputsCheck;
			
			//Retrieving Corresponding Petri Net from MongoDB
			try {
				Mongo mongo = new Mongo("localhost");
				Datastore petriNets = new Morphia().createDatastore(mongo, "PetriNetsManager");
				PetriNet retreivedPN = petriNets.get(PetriNet.class, reconfigInputs.get("ServiceName"));
				System.out.println("Retreived Petri Net Name: "+retreivedPN.getName());
				
				//Getting places from the service to be reconfigured
				for (Place p : retreivedPN.getPlaces()){
					if(p.getMainControlFlow() == Integer.parseInt(reconfigInputs.get("mainControlFlow"))){
						if(p.getBranchId() == 0){
							IOPlaces.add(p);
						} else{
							if(compareBranchId(reconfigInputs, p)){
								IOPlaces.add(p);
							}
						}
					}
				}
				
				System.out.println("*****************RETRIEVED PLACES**********************");
				for(Place p : IOPlaces){
					System.out.println("Retrieved Place Name: "+ p.getIdentifier());
					for(Token t : p.getTokens()){
						System.out.println("Parameter Type: "+t.getType()+" coming from: "+t.getSource()+" going to: "+t.getDestiny());
					}
				}
				System.out.println("*****************RETRIEVED PLACES**********************");
				
				//Storing I/O Places from service to be reconfigured individually
				if(IOPlaces.get(0).getIdentifier().indexOf("InputPlace") >= 0){
					reconfigInputPlace = IOPlaces.get(0);
					reconfigOutputPlace = IOPlaces.get(1);
				} else{
					reconfigOutputPlace = IOPlaces.get(0);
					reconfigInputPlace = IOPlaces.get(1);
				}
				
				//Retrieving Operation object from MongoDB Operations Repository
				Datastore operationsRep = new Morphia().createDatastore(mongo, "OperationsManager");
				operationName = IOPlaces.get(0).getName().substring(0, IOPlaces.get(0).getName().length() - 1);
				reconfigOperation = operationsRep.find(Operation.class).field("operationName").equal(operationName).get();
				System.out.println(" ");
				System.out.println(" ");
				System.out.println("Operation to reconfigure retrieved from repository: "+reconfigOperation.getOperationName());
				
				//Retrieving candidate operations to replace reconfigurated service from Repository
				candidateOperations = operationsRep.find(Operation.class).field("category").equal(reconfigOperation.getCategory()).asList();
				for(Operation op : candidateOperations){
					//Discarding reconfigurated Operation as a candidate
					if(op.getId() != reconfigOperation.getId()){
						System.out.println("Candidate Operation retrieved for Repository: "+op.getOperationName());
						//Outputs Analysis
						outputsCheck = outputAnalysis(reconfigOperation, op);
						//Inputs Analysis
						inputsCheck = inputAnalysis(op, reconfigInputPlace, retreivedPN.getPlaces(), operationsRep);
						System.out.println("******************RESULT*******************");
						System.out.println("Outputs check: "+outputsCheck);
						System.out.println("Inputs check: "+inputsCheck);
						System.out.println("******************RESULT*******************");
					}
				}
			
				mongo.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	private boolean compareBranchId(HashMap<String, String> reconfig, Place p){
		if(Integer.parseInt(reconfig.get("branchControlFlow"+Integer.toString(p.getBranchId()))) == p.getBranchControlFlow() 
				&& p.getName().indexOf(reconfig.get("operationName")) >= 0){
			return true;
		} else{
			return false;
		}
	}
	
	private boolean outputAnalysis(Operation reconfigOp, Operation candidateOp){
		HashMap<String, String> outputsCheck = new HashMap<String, String>();
		boolean check = true;
		
		for(Output rout : reconfigOp.getOutputs()){
			outputsCheck.put(rout.getOutputName(), "false");
			for(Output cout: candidateOp.getOutputs()){
				if(rout.getType().equals(cout.getType()) && (rout.getSubType().equals(cout.getSubType()) || 
						rout.getSubType().equals("any")) && rout.getDataType().equals(cout.getDataType())){
					outputsCheck.put(rout.getOutputName(), "true");
				}
			}
		}
		
		Iterator<Entry<String, String>> it = outputsCheck.entrySet().iterator();
		
		System.out.println("*******************OUTPUTS CHECK**********************");
		while(it.hasNext()){
			Entry<String, String> e = (Entry<String, String>) it.next();
			System.out.println("Output: "+e.getKey()+" is satisfied? "+e.getValue());
			if(e.getValue().equals("false")){
				check = false;
			}
		}
		System.out.println("*******************OUTPUTS CHECK**********************");
		return check;
	}
	
	private boolean inputAnalysis(Operation candidateOp, Place reconfigInputPlace, ArrayList<Place> places, Datastore operationsRep){
		HashMap<String, String> inputsCheck = new HashMap<String, String>();
		boolean check = true;
		
		for(Input cin : candidateOp.getInputs()){
			inputsCheck.put(cin.getInputName(), "false");
			System.out.println("Candidate Input: "+cin.getInputName());
		}
		
		if(inputsCheck.size() > 0){
			for(Place p : places){
				if((p.getMainControlFlow() < reconfigInputPlace.getMainControlFlow() || 
						(p.getBranchId() != 0 && p.getBranchId() == reconfigInputPlace.getBranchId())) && 
						(p.getIdentifier().indexOf("OutputPlace") >= 0 || p.getIdentifier().indexOf("StartPlace") >= 0)){
					for(Token t : p.getTokens()){
						Input i = getDestinyInput(t.getDestiny(), places, operationsRep);
						if(i != null){
							for(Input i0 : candidateOp.getInputs()){
								if(i0.getType().equals(i.getType()) && (i0.getSubType().equals(i.getSubType()) 
										|| i0.getSubType().equals("any")) && i0.getDataType().equals(i.getDataType())){
									inputsCheck.put(i0.getInputName(), "true");
								}
							}
						}
					}
				}
			}
		} else{
			check = false;
		}
		
		System.out.println("*******************INTPUTS CHECK**********************");
		Iterator<Entry<String, String>> it = inputsCheck.entrySet().iterator();
		while(it.hasNext()){
			Entry<String, String> e = (Entry<String, String>) it.next();
			System.out.println("Input: "+e.getKey()+" is satisfied? "+e.getValue());
			if(e.getValue().equals("false")){
				check = false;
			}
		}
		System.out.println("*******************INTPUTS CHECK**********************");
		return check;
	}
	
	private Input getDestinyInput(String destiny, ArrayList<Place> places, Datastore operationsRep){
		Input in = null;
		
		main: for(Place p : places){
			for(Token t : p.getTokens()){
				if(t.getType().equals("input") && t.getSource().equals(destiny)){
					Operation op = operationsRep.find(Operation.class).field("operationName")
							.equal(p.getName().substring(0, p.getName().length() - 1)).get();
					for(Input i : op.getInputs()){
						if(i.getInputName().equals(t.getDestiny())){
							in = i;
							break main;
						}
					}
				}
			}
		}
		return in;
	}
	
	// TODO: Perform further operations if required in these methods.
	public void setSbbContext(SbbContext context) { 
		this.sbbContext = context;
		try {
			Context ctx = (Context) new InitialContext().lookup("java:comp/env");
			saf = (ServiceActivityFactory) ctx.lookup("slee/serviceactivity/factory"); 
		} catch (NamingException e) {
			e.printStackTrace();
		}
	}
    public void unsetSbbContext() { this.sbbContext = null; }
    
    // TODO: Implement the lifecycle methods if required
    public void sbbCreate() throws javax.slee.CreateException {}
    public void sbbPostCreate() throws javax.slee.CreateException {}
    public void sbbActivate() {}
    public void sbbPassivate() {}
    public void sbbRemove() {}
    public void sbbLoad() {}
    public void sbbStore() {}
    public void sbbExceptionThrown(Exception exception, Object event, ActivityContextInterface activity) {}
    public void sbbRolledBack(RolledBackContext context) {}
	

	
	/**
	 * Convenience method to retrieve the SbbContext object stored in setSbbContext.
	 * 
	 * TODO: If your SBB doesn't require the SbbContext object you may remove this 
	 * method, the sbbContext variable and the variable assignment in setSbbContext().
	 *
	 * @return this SBB's SbbContext object
	 */
	
	protected SbbContext getSbbContext() {
		return sbbContext;
	}

	private SbbContext sbbContext; // This SBB's SbbContext

}
