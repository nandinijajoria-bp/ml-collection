package com.bharatpe.lending.dto;

import java.util.Arrays;
import java.util.List;

public class DigioEnachInitiationRequestDTO {
	private String corporate_config_id="TSE2002171645285123X1WYFR8P6Z5ER";
	
	private String mandate_type="create";
	
	private String auth_mode="api";
	
	private List<String> allowed_auth_modes=Arrays.asList(new String[]{"api"});
	
	private String customer_identifier;
	
	private Data mandate_data;
	
	public static class Data{
		private String destination_bank_id;
		
		private String management_category="L001";
		
		private String instrument_type="debit";
		
		private String frequency="Adhoc";
		
		private String first_collection_date;
		
		private String destination_bank_name;
		
		private String customer_name;
		
		private String customer_mobile;
		
		private String customer_email="";
		
		private String customer_pan;
		
		private String customer_account_number;
		
		private String customer_account_type;
		
		private Boolean is_recurring;
		
		private Integer maximum_amount;
		
		
		public String getDestination_bank_id() {
			return destination_bank_id;
		}


		public void setDestination_bank_id(String destination_bank_id) {
			this.destination_bank_id = destination_bank_id;
		}


		public String getManagement_category() {
			return management_category;
		}


		public void setManagement_category(String management_category) {
			this.management_category = management_category;
		}


		public String getInstrument_type() {
			return instrument_type;
		}


		public void setInstrument_type(String instrument_type) {
			this.instrument_type = instrument_type;
		}


		public String getFrequency() {
			return frequency;
		}


		public void setFrequency(String frequency) {
			this.frequency = frequency;
		}


		public String getFirst_collection_date() {
			return first_collection_date;
		}


		public void setFirst_collection_date(String first_collection_date) {
			this.first_collection_date = first_collection_date;
		}


		public String getDestination_bank_name() {
			return destination_bank_name;
		}


		public void setDestination_bank_name(String destination_bank_name) {
			this.destination_bank_name = destination_bank_name;
		}


		public String getCustomer_name() {
			return customer_name;
		}


		public void setCustomer_name(String customer_name) {
			this.customer_name = customer_name;
		}


		public String getCustomer_mobile() {
			return customer_mobile;
		}


		public void setCustomer_mobile(String customer_mobile) {
			this.customer_mobile = customer_mobile;
		}


		public String getCustomer_email() {
			return customer_email;
		}


		public void setCustomer_email(String customer_email) {
			this.customer_email = customer_email;
		}


		public String getCustomer_pan() {
			return customer_pan;
		}


		public void setCustomer_pan(String customer_pan) {
			this.customer_pan = customer_pan;
		}


		public String getCustomer_account_number() {
			return customer_account_number;
		}


		public void setCustomer_account_number(String customer_account_number) {
			this.customer_account_number = customer_account_number;
		}


		public String getCustomer_account_type() {
			return customer_account_type;
		}


		public void setCustomer_account_type(String customer_account_type) {
			this.customer_account_type = customer_account_type;
		}


		public Boolean getIs_recurring() {
			return is_recurring;
		}


		public void setIs_recurring(Boolean is_recurring) {
			this.is_recurring = is_recurring;
		}


		public Integer getMaximum_amount() {
			return maximum_amount;
		}


		public void setMaximum_amount(Integer maximum_amount) {
			this.maximum_amount = maximum_amount;
		}


		@Override
		public String toString() {
			return "Data [destination_bank_id=" + destination_bank_id + ", management_category=" + management_category
					+ ", instrument_type=" + instrument_type + ", frequency=" + frequency + ", first_collection_date="
					+ first_collection_date + ", destination_bank_name=" + destination_bank_name + ", customer_name="
					+ customer_name + ", customer_mobile=" + customer_mobile + ", customer_email=" + customer_email
					+ ", customer_pan=" + customer_pan + ", customer_account_number=" + customer_account_number
					+ ", customer_account_type=" + customer_account_type + ", is_recurring=" + is_recurring
					+ ", maximum_amount=" + maximum_amount + "]";
		}
		
		
		
	}

	public String getCorporate_config_id() {
		return corporate_config_id;
	}

	public void setCorporate_config_id(String corporate_config_id) {
		this.corporate_config_id = corporate_config_id;
	}

	public String getMandate_type() {
		return mandate_type;
	}

	public void setMandate_type(String mandate_type) {
		this.mandate_type = mandate_type;
	}

	public String getAuth_mode() {
		return auth_mode;
	}

	public void setAuth_mode(String auth_mode) {
		this.auth_mode = auth_mode;
	}

	public List<String> getAllowed_auth_modes() {
		return allowed_auth_modes;
	}

	public void setAllowed_auth_modes(List<String> allowed_auth_modes) {
		this.allowed_auth_modes = allowed_auth_modes;
	}

	public String getCustomer_identifier() {
		return customer_identifier;
	}

	public void setCustomer_identifier(String customer_identifier) {
		this.customer_identifier = customer_identifier;
	}

	public Data getMandate_data() {
		return mandate_data;
	}

	public void setMandate_data(Data mandate_data) {
		this.mandate_data = mandate_data;
	}

	@Override
	public String toString() {
		return "DigioEnachInitiationRequestDTO [corporate_config_id=" + corporate_config_id + ", mandate_type="
				+ mandate_type + ", auth_mode=" + auth_mode + ", allowed_auth_modes=" + allowed_auth_modes
				+ ", customer_identifier=" + customer_identifier + ", mandate_data=" + mandate_data + "]";
	}
	
	
	
	
}
