/*
 * Created on 07.09.2006
 *
 */
package de.moonflower.jfritz.callerlist.filter;

import java.util.Vector;

import de.moonflower.jfritz.struct.Call;

public class CallByCallFilter extends CallFilter {

    private Vector filteredCallByCallProviders = new Vector();

    public CallByCallFilter(Vector providers) {
    	filteredCallByCallProviders = providers;
    }

    public boolean passInternFilter(Call currentCall) {
        if (currentCall.getPhoneNumber() != null) {
            String currentProvider = currentCall.getPhoneNumber()
                    .getCallByCall();
//            Debug.msg("currentProvider: "+currentProvider);
            if (currentProvider.equals("")) { //$NON-NLS-1$
                return false;
            }
            if (filteredCallByCallProviders
                    .contains(currentProvider)) {
                return true;
            }
        }
        return false;
    }
    public String toString(){
    	String result="";
    	for(int i =0; i<filteredCallByCallProviders.size();i++){
    		result +=" "+filteredCallByCallProviders.elementAt(i);
    	}
    	return result;
    }

	public void setCallbyCallProvider(Vector callByCallProvider) {
		filteredCallByCallProviders = callByCallProvider;

	}
}
