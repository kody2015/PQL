package org.pql.core;

import java.sql.CallableStatement;
import java.sql.SQLException;
import org.deckfour.xes.model.XLog;
import org.jbpt.persist.MySQLConnection;
import org.pql.alignment.AbstractReplayer;
import org.pql.alignment.PQLAlignment;
import org.pql.alignment.Replayer;
import org.pql.api.AlignmentAPI;
import org.pql.logic.IThreeValuedLogic;
import org.pql.logic.ThreeValuedLogicValue;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.impl.PetrinetFactory;

/**
 * @author Artem Polyvyanyy
 */
public class PQLBasicPredicatesMySQL
				extends MySQLConnection
				implements IPQLBasicPredicatesOnTasks {
	
	protected String PETRI_NET_IDENTIFIER_TO_ID = "{? = CALL pql.jbpt_petri_nets_get_internal_id(?)}";
	protected String PQL_CAN_OCCUR		= "{? = CALL pql.pql_can_occur(?,?)}";
	protected String PQL_ALWAYS_OCCURS	= "{? = CALL pql.pql_always_occurs(?,?)}";
	protected String PQL_CAN_CONFLICT	= "{? = CALL pql.pql_can_conflict(?,?,?)}";
	protected String PQL_CAN_COOCCUR	= "{? = CALL pql.pql_can_cooccur(?,?,?)}";
	protected String PQL_TOTAL_CAUSAL	= "{? = CALL pql.pql_total_causal(?,?,?)}";
	protected String PQL_TOTAL_CONCUR	= "{? = CALL pql.pql_total_concur(?,?,?)}";
		
	private IThreeValuedLogic	logic = null;
	private String				identifier = null;	
	private int					netID = 0;
	
	public PQLBasicPredicatesMySQL(String mysqlURL, String mysqlUser, String mysqlPassword, IThreeValuedLogic logic) throws ClassNotFoundException, SQLException {
		super(mysqlURL,mysqlUser,mysqlPassword);

		this.logic = logic;
	}
	
	private ThreeValuedLogicValue checkUnaryPredicate(String call, PQLTask task) {
		try {
			CallableStatement cs = connection.prepareCall(call);
		
			cs.registerOutParameter(1, java.sql.Types.TINYINT);
			cs.setInt(2,this.netID);
			cs.setInt(3,task.getID());
						
			cs.execute();
			
			ThreeValuedLogicValue result = null;
			
			if (cs.getInt(1)==0)
				result = ThreeValuedLogicValue.FALSE;
			else if (cs.getInt(1)==1) 
				result = ThreeValuedLogicValue.TRUE;
			else
				return ThreeValuedLogicValue.UNKNOWN;
			
			cs.close();
			
			return result;
		}
		catch (SQLException e) {
			return ThreeValuedLogicValue.UNKNOWN;
		}
	}
	
	//A.P.
	private ThreeValuedLogicValue checkUnaryTracePredicate(PQLTrace trace) {
		try {
				
			ThreeValuedLogicValue 		result 				= null;
			XLog 						log					= null; 
			PetrinetGraph				net 				= PetrinetFactory.newPetrinet("PNML");
				
			// TO DO: label unification
			
			// TO DO: transform the model to consider '*' in traces
						
			// TO DO: create PetrinetGraph from the transformed model
			
			// get original PNML from DB: column 'pnml_content' in table 'jbpt_petri_nets' 
			String pnml = AlignmentAPI.getPnmlContent(this.netID);
			byte[] pnmlBytes = pnml.getBytes();
			
			// create PetrinetGraph from PNML
			net = AlignmentAPI.constructNet(pnmlBytes, net);
			
			// convert trace to XLog
			log = AlignmentAPI.getLog(trace);	
			
			// get an optimal alignment
			AbstractReplayer replayer = new Replayer();
			PQLAlignment alignment = replayer.getAlignment(net, log);
						
			// get alignment cost
			int alignmentCost = alignment.getAlignmentCost();
						
			if(alignmentCost == 0)
			{result = ThreeValuedLogicValue.TRUE;}else{result = ThreeValuedLogicValue.FALSE;}
			return result;
			}
			catch(Exception e){return ThreeValuedLogicValue.UNKNOWN;}
		
	}
	
	private ThreeValuedLogicValue checkBinaryPredicate(String call, PQLTask taskA, PQLTask taskB) {
		try {
			CallableStatement cs = connection.prepareCall(call);
		
			cs.registerOutParameter(1, java.sql.Types.TINYINT);
			cs.setInt(2,this.netID);
			cs.setInt(3,taskA.getID());
			cs.setInt(4,taskB.getID());
			
			cs.execute();
			
			ThreeValuedLogicValue result = null;
			
			if (cs.getInt(1)==0)
				result = ThreeValuedLogicValue.FALSE;
			else if (cs.getInt(1)==1) 
				result = ThreeValuedLogicValue.TRUE;
			else
				return ThreeValuedLogicValue.UNKNOWN;
			
			cs.close();
			
			return result;
		}
		catch (SQLException e) {
			return ThreeValuedLogicValue.UNKNOWN;
		}
	}
	
	@Override
	public void configure(Object param) {
		this.identifier = (String) param;
		
		try {
			CallableStatement cs = connection.prepareCall(this.PETRI_NET_IDENTIFIER_TO_ID);
			
			cs.registerOutParameter(1, java.sql.Types.INTEGER);
			cs.setString(2, identifier);
			
			cs.execute();
			
			this.netID = cs.getInt(1);
		}
		catch (SQLException e) {
			e.printStackTrace();
		}
	}

	@Override
	public ThreeValuedLogicValue canOccur(PQLTask task) {
		return this.checkUnaryPredicate(this.PQL_CAN_OCCUR, task);
	}
	
	@Override
	public ThreeValuedLogicValue alwaysOccurs(PQLTask task) {
		return this.checkUnaryPredicate(this.PQL_ALWAYS_OCCURS, task);
	}
	
	//A.P.
	@Override
	public ThreeValuedLogicValue executes(PQLTrace trace) {
		return this.checkUnaryTracePredicate(trace);
	}


	@Override
	public ThreeValuedLogicValue canConflict(PQLTask taskA, PQLTask taskB) {
		return this.checkBinaryPredicate(this.PQL_CAN_CONFLICT, taskA, taskB);
	}

	@Override
	public ThreeValuedLogicValue canCooccur(PQLTask taskA, PQLTask taskB) {
		return this.checkBinaryPredicate(this.PQL_CAN_COOCCUR, taskA, taskB);
	}

	/**
	 * See Def. 4.2. in Artem Polyvyanyy, Matthias Weidlich, Raffaele Conforti, Marcello La Rosa, Arthur H. M. ter Hofstede: The 4C Spectrum of Fundamental Behavioral Relations for Concurrent Systems. Petri Nets 2014:210-232
	 */
	@Override
	public ThreeValuedLogicValue conflict(PQLTask taskA, PQLTask taskB) {
		return logic.AND(logic.AND(this.canConflict(taskA, taskB), this.canConflict(taskB, taskA)), logic.NOT(this.canCooccur(taskA,taskB)));
	}

	/**
	 * See Def. 4.2. in Artem Polyvyanyy, Matthias Weidlich, Raffaele Conforti, Marcello La Rosa, Arthur H. M. ter Hofstede: The 4C Spectrum of Fundamental Behavioral Relations for Concurrent Systems. Petri Nets 2014:210-232
	 */
	@Override
	public ThreeValuedLogicValue cooccur(PQLTask taskA, PQLTask taskB) {
		return logic.AND(logic.AND(logic.NOT(this.canConflict(taskA, taskB)),logic.NOT(this.canConflict(taskB, taskA))), this.canCooccur(taskA,taskB));
	}

	@Override
	public ThreeValuedLogicValue totalCausal(PQLTask taskA, PQLTask taskB) {
		return this.checkBinaryPredicate(this.PQL_TOTAL_CAUSAL, taskA, taskB);
	}

	@Override
	public ThreeValuedLogicValue totalConcur(PQLTask taskA, PQLTask taskB) {
		return this.checkBinaryPredicate(this.PQL_TOTAL_CONCUR, taskA, taskB);
	}

}
