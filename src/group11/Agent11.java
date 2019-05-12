package group11;
import java.util.*;

import agents.uk.ac.soton.ecs.gp4j.util.ArrayUtils;
import genius.core.Bid;
import genius.core.actions.Accept;
import genius.core.actions.Action;
import genius.core.actions.Offer;
import genius.core.issue.Issue;
import genius.core.issue.IssueDiscrete;
import genius.core.issue.Value;
import genius.core.issue.ValueDiscrete;
import genius.core.parties.AbstractNegotiationParty;
import genius.core.parties.NegotiationInfo;
import genius.core.uncertainty.AdditiveUtilitySpaceFactory;
import genius.core.utility.AbstractUtilitySpace;


public class Agent11 extends AbstractNegotiationParty {
    private class AgentModel {
        private double Issues [][];
        private double Weights [];
        public int frequency [][];
        private int N_issues;
        private int N_values;
        private int maximum_freqs [];
        private int weight_ranking[];
        private int value_ranking[];

        //build a class to estimate opponent's utility
        public AgentModel(int num_issues, int num_values, int [][] freq, List<Issue> domain_issues) {
            Issues = new double [num_issues][num_values];
            Weights = new double [num_issues];
            frequency = freq;
            N_issues = num_issues;
            N_values = num_values;
            maximum_freqs = new int[N_issues];
            weight_ranking = new int [N_issues];
            value_ranking = new int [N_values];
        }
        //update the model
        public void updateModel() {
            int i,j,k;
            for(i = 0; i < N_issues; i++) {
                maximum_freqs[i] = Collections.max(Arrays.asList(ArrayUtils.toObject(frequency[i])));
                if(maximum_freqs[i] == 0) {
                    maximum_freqs[i] = 1;
                }
                weight_ranking[i] = N_issues-i;
                j = i - 1;
                while(j != -1) {
                    if(maximum_freqs[i] > maximum_freqs[j]) {
                        weight_ranking[i] = Math.max(weight_ranking[j], weight_ranking[i]);
                        weight_ranking[j]--;
                    }
                    j--;
                }
                for(j = 0; j < N_values; j++) {
                    value_ranking[j] = N_values-j;
                    k = j - 1;
                    while(k != -1) {
                        if(frequency[i][j] > frequency[i][k]) {
                            value_ranking[j]++;
                            value_ranking[k]--;
                        }
                        if(frequency[i][j] == frequency[i][k]) {
                            value_ranking[k]--;
                            value_ranking[j] = Math.min(value_ranking[k], value_ranking[j]);
                        }
                        k--;
                    }
                }
                for(j = 0; j < N_values; j++) {
                    Issues[i][j] = value_ranking[j]*1.0/N_values;//Computer estimated values
                }
            }
            for(i = 0; i < N_issues; i++) {
                Weights[i] = 2.0*weight_ranking[i]/(N_issues*(N_issues+1.0));//Compute estimated weights
            }
        }
        //predict a bid's utility value of opponent
        public double predictUtility(Bid bid) {
            double U = 0.0;
            for(Issue issue: bid.getIssues()) {
                U = U +Issues[issue.getNumber()-1][((IssueDiscrete) issue).getValueIndex((ValueDiscrete)(bid.getValue(issue.getNumber())))] *Weights[issue.getNumber()-1];
            }
            return U;
        }
    }
    private AgentModel agentmodel;
    private Bid lastReceivedOffer=null;
    private Bid myLastOffer;
    Bid curr_bid ;
    private int number_of_issues;
    private java.util.List<Issue> domain_issues;
    private java.util.List<ValueDiscrete> values;
    int freq [][];
    int turn=0;


    public void init(NegotiationInfo info) {
        super.init(info);
        curr_bid = generateRandomBid();
        domain_issues = this.utilitySpace.getDomain().getIssues();
        number_of_issues = domain_issues.size();
        System.out.format("Domain has %d issues\n ", domain_issues.size());
        int max_num_of_values;
        max_num_of_values = 0;
        for(Issue lIssue : domain_issues) {
            IssueDiscrete lIssueDiscrete = (IssueDiscrete) lIssue;
            values = lIssueDiscrete.getValues();
            if (values.size() > max_num_of_values) {
                max_num_of_values = values.size();
            }
        }
        freq = new int [domain_issues.size()][max_num_of_values];
        agentmodel = new AgentModel(domain_issues.size(), max_num_of_values, freq, domain_issues);
        for(int i = 0 ; i < domain_issues.size() ; i ++){
            for(int j = 0 ; j < max_num_of_values ; j++){
                freq [i][j] = 0;
            }
        }
    }

    @Override
    public Action chooseAction(List<Class<? extends Action>> possibleActions) {
        turn+=1;
        if(lastReceivedOffer == null) {
            System.out.println("\nOffering random Bid at the beginning");
            myLastOffer = generateRandomBid();
            lastReceivedOffer=myLastOffer;
            return new Offer(this.getPartyId(), myLastOffer);
        }else {
            lastReceivedOffer= ((Offer) getLastReceivedAction()).getBid();
            update_freq(lastReceivedOffer);
            agentmodel.updateModel();
            double test=agentmodel.predictUtility(lastReceivedOffer);
            System.out.printf("The test of predict is %f",test);
            if (getTimeLine().getTime() < 0.3) {
                return new Offer(getPartyId(), generateRandomBid());
            } else if (gettheUtility(lastReceivedOffer)>0.8) {
                return new Accept(this.getPartyId(), lastReceivedOffer);    

            } else if (getTimeLine().getTime() > 0.95 && gettheUtility(lastReceivedOffer)>= this.getTargetUtility()) {
                return new Accept(this.getPartyId(), lastReceivedOffer);
            }
            myLastOffer =generateBid();
            return new Offer(this.getPartyId(), myLastOffer);
        }
    }

    private void log(String s) {
        System.out.println(s);
    }

    @Override
    public AbstractUtilitySpace estimateUtilitySpace() {
        return new AdditiveUtilitySpaceFactory(getDomain()).getUtilitySpace();
    }
    public double getTargetUtility() {
        return 1-0.5*getTimeLine().getTime();
    }

    //estimate own utility
    public double gettheUtility(Bid bid){
        List<Bid> bidOrder = userModel.getBidRanking().getBidOrder();

        if (bidOrder.contains(bid)) {
            double percentile =  bidOrder.indexOf(bid)
                    / (double) bidOrder.size();
            return percentile+0.1;
        }else{
            return 0.55;
        }
    }

    public void update_freq (Bid curr_bid_freq ){

        java.util.List<Issue> bid_issues;
        java.util.HashMap<java.lang.Integer, Value> 	bid_values;
        IssueDiscrete lIssueDiscrete ;
        ValueDiscrete lValueDiscrete;
        bid_issues = curr_bid_freq.getIssues();
        bid_values = curr_bid_freq.getValues();


        for (Integer curr_key : bid_values.keySet()){
            lIssueDiscrete = (IssueDiscrete) (bid_issues.get(curr_key -1));
            lValueDiscrete = (ValueDiscrete) bid_values.get(curr_key);
            freq [curr_key-1][lIssueDiscrete.getValueIndex(lValueDiscrete.getValue())] ++;
        }

    }

    private Bid generateBid(){
        double T , T_old, oldScore , newScore , myUtility, predicted=0 ;
        Bid new_bid;
        int random_issue;
        HashMap<Integer, Value> curr_bid_value = new HashMap<Integer, Value>();
        HashMap<Integer,Value> 	bid_values;
        Random randomnr = new Random();
        for(int k = 1 ;k<600  ; k++){
            myUtility = this.gettheUtility(curr_bid);
            predicted= agentmodel.predictUtility(curr_bid);
            oldScore = (1.0/Math.max(0.1, Math.abs(myUtility - predicted)))+  (myUtility);
            bid_values = curr_bid.getValues();
            random_issue = randomnr.nextInt(number_of_issues);
            for(Issue lIssue : domain_issues){
                IssueDiscrete lIssueDiscrete = (IssueDiscrete) lIssue;
                if( random_issue != lIssue.getNumber() ){
                    curr_bid_value.put(lIssue.getNumber() , bid_values.get(lIssue.getNumber()));}
                else {
                    curr_bid_value.put(lIssue.getNumber() , lIssueDiscrete.getValue(randomnr.nextInt( lIssueDiscrete.getNumberOfValues())));
                }
            }
            new_bid = new Bid(utilitySpace.getDomain(), curr_bid_value);
            myUtility = this.gettheUtility(new_bid);
            predicted = agentmodel.predictUtility(new_bid);
            newScore =(1.0/Math.max(0.1, Math.abs(myUtility - predicted))) + (myUtility);
            if(this.gettheUtility(new_bid) < 0.9 && this.gettheUtility(new_bid) > getTargetUtility() ){
                if(newScore > oldScore ){
                    curr_bid = new_bid;
                    break;
                }
            }
        }
        return curr_bid;
    }
    @Override
    public String getDescription() {
        return "Negotation agent of group 11 which can deal with uncertain preferences";
    }
}
