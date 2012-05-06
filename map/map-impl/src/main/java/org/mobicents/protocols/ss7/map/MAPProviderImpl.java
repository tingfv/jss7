/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat, Inc. and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.mobicents.protocols.ss7.map;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.log4j.Logger;
import org.mobicents.protocols.asn.AsnException;
import org.mobicents.protocols.asn.AsnInputStream;
import org.mobicents.protocols.asn.AsnOutputStream;
import org.mobicents.protocols.ss7.map.api.MAPApplicationContext;
import org.mobicents.protocols.ss7.map.api.MAPApplicationContextVersion;
import org.mobicents.protocols.ss7.map.api.MAPDialog;
import org.mobicents.protocols.ss7.map.api.MAPDialogListener;
import org.mobicents.protocols.ss7.map.api.MAPDialogueAS;
import org.mobicents.protocols.ss7.map.api.MAPException;
import org.mobicents.protocols.ss7.map.api.MAPParameterFactory;
import org.mobicents.protocols.ss7.map.api.MAPParsingComponentException;
import org.mobicents.protocols.ss7.map.api.MAPProvider;
import org.mobicents.protocols.ss7.map.api.MAPServiceBase;
import org.mobicents.protocols.ss7.map.api.MAPSmsTpduParameterFactory;
import org.mobicents.protocols.ss7.map.api.dialog.MAPAbortProviderReason;
import org.mobicents.protocols.ss7.map.api.dialog.MAPAbortSource;
import org.mobicents.protocols.ss7.map.api.dialog.MAPDialogState;
import org.mobicents.protocols.ss7.map.api.dialog.MAPNoticeProblemDiagnostic;
import org.mobicents.protocols.ss7.map.api.dialog.MAPProviderAbortReason;
import org.mobicents.protocols.ss7.map.api.dialog.MAPProviderError;
import org.mobicents.protocols.ss7.map.api.dialog.MAPRefuseReason;
import org.mobicents.protocols.ss7.map.api.dialog.MAPUserAbortChoice;
import org.mobicents.protocols.ss7.map.api.dialog.Reason;
import org.mobicents.protocols.ss7.map.api.dialog.ServingCheckData;
import org.mobicents.protocols.ss7.map.api.errors.MAPErrorCode;
import org.mobicents.protocols.ss7.map.api.errors.MAPErrorMessage;
import org.mobicents.protocols.ss7.map.api.errors.MAPErrorMessageFactory;
import org.mobicents.protocols.ss7.map.api.primitives.AddressString;
import org.mobicents.protocols.ss7.map.api.primitives.IMSI;
import org.mobicents.protocols.ss7.map.api.primitives.MAPExtensionContainer;
import org.mobicents.protocols.ss7.map.api.service.lsm.MAPServiceLsm;
import org.mobicents.protocols.ss7.map.api.service.sms.MAPServiceSms;
import org.mobicents.protocols.ss7.map.api.service.subscriberInformation.MAPServiceSubscriberInformation;
import org.mobicents.protocols.ss7.map.api.service.supplementary.MAPServiceSupplementary;
import org.mobicents.protocols.ss7.map.dialog.MAPAcceptInfoImpl;
import org.mobicents.protocols.ss7.map.dialog.MAPCloseInfoImpl;
import org.mobicents.protocols.ss7.map.dialog.MAPOpenInfoImpl;
import org.mobicents.protocols.ss7.map.dialog.MAPProviderAbortInfoImpl;
import org.mobicents.protocols.ss7.map.dialog.MAPRefuseInfoImpl;
import org.mobicents.protocols.ss7.map.dialog.MAPUserAbortInfoImpl;
import org.mobicents.protocols.ss7.map.errors.MAPErrorMessageFactoryImpl;
import org.mobicents.protocols.ss7.map.errors.MAPErrorMessageImpl;
import org.mobicents.protocols.ss7.map.service.lsm.MAPServiceLsmImpl;
import org.mobicents.protocols.ss7.map.service.sms.MAPServiceSmsImpl;
import org.mobicents.protocols.ss7.map.service.subscriberInformation.MAPServiceSubscriberInformationImpl;
import org.mobicents.protocols.ss7.map.service.supplementary.MAPServiceSupplementaryImpl;
import org.mobicents.protocols.ss7.tcap.api.TCAPProvider;
import org.mobicents.protocols.ss7.tcap.api.TCAPSendException;
import org.mobicents.protocols.ss7.tcap.api.TCListener;
import org.mobicents.protocols.ss7.tcap.api.tc.dialog.Dialog;
import org.mobicents.protocols.ss7.tcap.api.tc.dialog.events.TCBeginIndication;
import org.mobicents.protocols.ss7.tcap.api.tc.dialog.events.TCBeginRequest;
import org.mobicents.protocols.ss7.tcap.api.tc.dialog.events.TCContinueIndication;
import org.mobicents.protocols.ss7.tcap.api.tc.dialog.events.TCContinueRequest;
import org.mobicents.protocols.ss7.tcap.api.tc.dialog.events.TCEndIndication;
import org.mobicents.protocols.ss7.tcap.api.tc.dialog.events.TCEndRequest;
import org.mobicents.protocols.ss7.tcap.api.tc.dialog.events.TCNoticeIndication;
import org.mobicents.protocols.ss7.tcap.api.tc.dialog.events.TCPAbortIndication;
import org.mobicents.protocols.ss7.tcap.api.tc.dialog.events.TCUniIndication;
import org.mobicents.protocols.ss7.tcap.api.tc.dialog.events.TCUserAbortIndication;
import org.mobicents.protocols.ss7.tcap.api.tc.dialog.events.TCUserAbortRequest;
import org.mobicents.protocols.ss7.tcap.api.tc.dialog.events.TerminationType;
import org.mobicents.protocols.ss7.tcap.asn.ApplicationContextName;
import org.mobicents.protocols.ss7.tcap.asn.DialogServiceProviderType;
import org.mobicents.protocols.ss7.tcap.asn.DialogServiceUserType;
import org.mobicents.protocols.ss7.tcap.asn.InvokeImpl;
import org.mobicents.protocols.ss7.tcap.asn.ResultSourceDiagnostic;
import org.mobicents.protocols.ss7.tcap.asn.TcapFactory;
import org.mobicents.protocols.ss7.tcap.asn.UserInformation;
import org.mobicents.protocols.ss7.tcap.asn.comp.Component;
import org.mobicents.protocols.ss7.tcap.asn.comp.ComponentType;
import org.mobicents.protocols.ss7.tcap.asn.comp.ErrorCodeType;
import org.mobicents.protocols.ss7.tcap.asn.comp.Invoke;
import org.mobicents.protocols.ss7.tcap.asn.comp.InvokeProblemType;
import org.mobicents.protocols.ss7.tcap.asn.comp.OperationCode;
import org.mobicents.protocols.ss7.tcap.asn.comp.OperationCodeType;
import org.mobicents.protocols.ss7.tcap.asn.comp.PAbortCauseType;
import org.mobicents.protocols.ss7.tcap.asn.comp.Parameter;
import org.mobicents.protocols.ss7.tcap.asn.comp.Problem;
import org.mobicents.protocols.ss7.tcap.asn.comp.ProblemType;
import org.mobicents.protocols.ss7.tcap.asn.comp.Reject;
import org.mobicents.protocols.ss7.tcap.asn.comp.ReturnError;
import org.mobicents.protocols.ss7.tcap.asn.comp.ReturnErrorProblemType;
import org.mobicents.protocols.ss7.tcap.asn.comp.ReturnResult;
import org.mobicents.protocols.ss7.tcap.asn.comp.ReturnResultLast;
import org.mobicents.protocols.ss7.tcap.asn.comp.ReturnResultProblemType;

/**
 * 
 * @author amit bhayani
 * @author sergey vetyutnev
 * 
 */
public class MAPProviderImpl implements MAPProvider, TCListener {

	protected static final Logger loger = Logger.getLogger(MAPProviderImpl.class);

	private transient List<MAPDialogListener> dialogListeners = new CopyOnWriteArrayList<MAPDialogListener>();

	protected transient Map<Long, MAPDialogImpl> dialogs = new HashMap<Long, MAPDialogImpl>();

	/**
	 * Congestion sources name list. Congestion is where this collection is not empty
	 */
	protected transient Map<String, String> congSources = new HashMap<String, String>();

	private transient TCAPProvider tcapProvider = null;

	private final transient MAPParameterFactory MAPParameterFactory = new MAPParameterFactoryImpl();
	private final transient MAPSmsTpduParameterFactory mapSmsTpduParameterFactory = new MAPSmsTpduParameterFactoryImpl();
	private final transient MAPErrorMessageFactory mapErrorMessageFactory = new MAPErrorMessageFactoryImpl();

	protected transient Set<MAPServiceBase> mapServices = new HashSet<MAPServiceBase>();
	private final transient MAPServiceSupplementary mapServiceSupplementary = new MAPServiceSupplementaryImpl(this);
	private final transient MAPServiceSms mapServiceSms = new MAPServiceSmsImpl(this);
	private final transient MAPServiceLsm mapServiceLsm = new MAPServiceLsmImpl(this);
	private final transient MAPServiceSubscriberInformation mapServiceSubscriberInformation = new MAPServiceSubscriberInformationImpl(this);

	/**
	 * public common methods
	 */

	public MAPProviderImpl(TCAPProvider tcapProvider) {
		this.tcapProvider = tcapProvider;

		this.mapServices.add(this.mapServiceSupplementary);
		this.mapServices.add(this.mapServiceSms);
		this.mapServices.add(this.mapServiceLsm);
		this.mapServices.add(this.mapServiceSubscriberInformation);
	}

	public TCAPProvider getTCAPProvider() {
		return this.tcapProvider;
	}

	public MAPServiceSupplementary getMAPServiceSupplementary() {
		return this.mapServiceSupplementary;
	}

	public MAPServiceSms getMAPServiceSms() {
		return this.mapServiceSms;
	}
	
	public MAPServiceLsm getMAPServiceLsm(){
		return this.mapServiceLsm;
	}
	
	/**
	 * @return the mapServiceSubscriberInformation
	 */
	public MAPServiceSubscriberInformation getMapServiceSubscriberInformation() {
		return mapServiceSubscriberInformation;
	}

	public void addMAPDialogListener(MAPDialogListener mapDialogListener) {
		this.dialogListeners.add(mapDialogListener);
	}

	public MAPParameterFactory getMAPParameterFactory() {
		return MAPParameterFactory;
	}

	public MAPSmsTpduParameterFactory getMAPSmsTpduParameterFactory() {
		return mapSmsTpduParameterFactory;
	}
	
	public MAPErrorMessageFactory getMAPErrorMessageFactory() {
		return this.mapErrorMessageFactory;
	}


	public void removeMAPDialogListener(MAPDialogListener mapDialogListener) {
		this.dialogListeners.remove(mapDialogListener);
	}

	public MAPDialog getMAPDialog(Long dialogId) {
		synchronized (this.dialogs) {
			return this.dialogs.get(dialogId);
		}
	}

	public void start() {
		this.tcapProvider.addTCListener(this);
	}

	public void stop() {
		this.tcapProvider.removeTCListener(this);

	}

	/**
	 * protected common methods
	 */

	protected void addDialog(MAPDialogImpl dialog) {
		synchronized (this.dialogs) {
			this.dialogs.put(dialog.getDialogId(), dialog);
		}
	}

	protected void removeDialog(Long dialogId) {
		synchronized (this.dialogs) {
			this.dialogs.remove(dialogId);
		}
	}
	
	public void onCongestionFinish(String congName) {
		synchronized (this.congSources) {
			this.congSources.put(congName, congName);
		}
	}

	public void onCongestionStart(String congName) {
		synchronized (this.congSources) {
			this.congSources.remove(congName);
		}
	}

	public boolean isCongested() {
		if (this.congSources.size() > 0)
			return true;
		else
			return false;
	}

	public void onTCBegin(TCBeginIndication tcBeginIndication) {
		
		ApplicationContextName acn = tcBeginIndication.getApplicationContextName();
		Component[] comps = tcBeginIndication.getComponents();

		// ETS 300 974 Section 12.1.3
		// On receipt of a TC-BEGIN indication primitive, the MAP PM shall:
		//
		// - if no application-context-name is included in the primitive and if
		// the "Components present" indicator indicates "no components", issue a
		// TC-U-ABORT request primitive (note 2). The local MAP-User is not
		// informed;
		if (acn == null && comps == null) {
			loger.error(String.format(
					"Received TCBeginIndication=%s, both ApplicationContextName and Component[] are null. Send TC-U-ABORT to peer and not notifying the User",
					tcBeginIndication));

			try {
				this.fireTCAbortV1(tcBeginIndication.getDialog(), false);
			} catch (MAPException e) {
				loger.error("Error while firing TC-U-ABORT. ", e);
			}
			return;
		}

		MAPApplicationContext mapAppCtx = null;
		MAPServiceBase perfSer = null;
		if (acn == null) {
			// ApplicationContext is absent but components are absent - MAP
			// Version 1

			// - if no application-context-name is included in the primitive and
			// if presence of components is indicated, wait for the first
			// TC-INVOKE primitive, and derive a version 1
			// application-context-name from the operation code according to
			// table 12.1/1 (note 1);

			// a) if no application-context-name can be derived (i.e. the
			// operation code does not exist in MAP V1 specifications), the MAP
			// PM shall issue a TC-U-ABORT request primitive (note 2). The local
			// MAP-User is not informed.

			// Extracting Invoke and operationCode
			Invoke invoke = null;
			int operationCode = -1;
			for (Component c : comps) {
				if (c.getType() == ComponentType.Invoke) {
					invoke = (Invoke) c;
					break;
				}
			}
			if (invoke != null) {
				OperationCode oc = invoke.getOperationCode();
				if (oc != null && oc.getOperationType() == OperationCodeType.Local) {
					operationCode = (int) (long) oc.getLocalOperationCode();
				}
			}
			if (operationCode != -1) {
				// Selecting the MAP service that can perform the operation, getting
				// ApplicationContext
				for (MAPServiceBase ser : this.mapServices) {
					MAPApplicationContext ac = ((MAPServiceBaseImpl)ser).getMAPv1ApplicationContext(operationCode, invoke);
					if (ac != null) {
						perfSer = ser;
						mapAppCtx = ac;
						break;
					}
				}
			}
			
			if (mapAppCtx == null) {
				// Invoke not found or has bad operationCode or operationCode is not supported
				try {
					this.fireTCAbortV1(tcBeginIndication.getDialog(), false);
					return;
				} catch (MAPException e) {
					loger.error("Error while firing TC-U-ABORT. ", e);
				}
			}
		} else {
			// ApplicationContext is present - MAP Version 2 or higher
			if (MAPApplicationContext.getProtocolVersion(acn.getOid()) < 2) {
				// if a version 1 application-context-name is included, the MAP
				// PM shall issue a TC-U-ABORT
				// request primitive with abort-reason "User-specific" and
				// user-information "MAP-ProviderAbortInfo"
				// indicating "abnormalDialogue". The local MAP-user shall not
				// be informed.
				loger.error("Bad version of ApplicationContext if ApplicationContext exists. Must be 2 or greater");
				try {
					this.fireTCAbortProvider(tcBeginIndication.getDialog(), MAPProviderAbortReason.abnormalDialogue, null, false);
				} catch (MAPException e) {
					loger.error("Error while firing TC-U-ABORT. ", e);
				}
				return;
			}
			
			mapAppCtx = MAPApplicationContext.getInstance(acn.getOid());
			
			// Check if ApplicationContext is recognizable for the implemented
			// services
			// If no - TC-U-ABORT - ACN-Not-Supported

			if (mapAppCtx == null) {
				StringBuffer s = new StringBuffer();
				s.append("Unrecognizable ApplicationContextName is received: ");
				for (long l : acn.getOid()) {
					s.append(l).append(", ");
				}

				loger.error(s.toString());
				try {
					this.fireTCAbortACNNotSupported(tcBeginIndication.getDialog(), null, null, false);
				} catch (MAPException e) {
					loger.error("Error while firing TC-U-ABORT. ", e);
				}

				return;
			}
		}

		AddressString destReference = null;
		AddressString origReference = null;
		MAPExtensionContainer extensionContainer = null;
		boolean eriStyle = false;
		IMSI eriImsi = null;
		AddressString eriVlrNo = null;

		UserInformation userInfo = tcBeginIndication.getUserInformation();
		if (userInfo == null) {
			// if no User-information is present it is checked whether
			// presence of User Information in the
			// TC-BEGIN indication primitive is required for the received
			// application-context-name. If User
			// Information is required but not present, a TC-U-ABORT request
			// primitive with abort-reason
			// "User-specific" and user-information "MAP-ProviderAbortInfo"
			// indicating "abnormalDialogue"
			// shall be issued. The local MAP-user shall not be informed.

			// TODO : From where do we know id userInfo is required for a
			// give
			// application-context-name?
			// May be if neither destinationReference nor
			// originationReference is needed
			// then no userInfo is needed (there is an
			// ApplicationContextName list in the specification)

			// TODO: Make a checking if MAP-OPEN is not needed -> continue
			// without sending TC-U-ABORT - how?
		} else {
			// if an application-context-name different from version 1 is
			// included in the primitive and if User-
			// information is present, the User-information must constitute
			// a syntactically correct MAP-OPEN
			// dialogue PDU. Otherwise a TC-U-ABORT request primitive with
			// abort-reason "User-specific" and
			// user-information "MAP-ProviderAbortInfo" indicating
			// "abnormalDialogue" shall be issued and the
			// local MAP-user shall not be informed.

			MAPOpenInfoImpl mapOpenInfoImpl = new MAPOpenInfoImpl();

			if (!userInfo.isOid()) {
				loger.error("When parsing TC-BEGIN: userInfo.isOid() check failed");
				try {
					this.fireTCAbortProvider(tcBeginIndication.getDialog(), MAPProviderAbortReason.abnormalDialogue, null, false);
				} catch (MAPException e) {
					loger.error("Error while firing TC-U-ABORT. ", e);
				}
				return;
			}

			long[] oid = userInfo.getOidValue();

			MAPDialogueAS mapDialAs = MAPDialogueAS.getInstance(oid);

			if (mapDialAs == null) {
				loger.error("When parsing TC-BEGIN: Expected MAPDialogueAS.MAP_DialogueAS but is null");
				try {
					this.fireTCAbortProvider(tcBeginIndication.getDialog(), MAPProviderAbortReason.abnormalDialogue, null, false);
				} catch (MAPException e) {
					loger.error("Error while firing TC-U-ABORT. ", e);
				}
				return;
			}

			if (!userInfo.isAsn()) {
				loger.error("When parsing TC-BEGIN: userInfo.isAsn() check failed");
				try {
					this.fireTCAbortProvider(tcBeginIndication.getDialog(), MAPProviderAbortReason.abnormalDialogue, null, false);
				} catch (MAPException e) {
					loger.error("Error while firing TC-U-ABORT. ", e);
				}
				return;
			}

			try {
				byte[] asnData = userInfo.getEncodeType();

				AsnInputStream ais = new AsnInputStream(asnData);

				int tag = ais.readTag();

				// It should be MAP_OPEN Tag
				if (tag != MAPOpenInfoImpl.MAP_OPEN_INFO_TAG) {
					loger.error("When parsing TC-BEGIN: MAP-OPEN dialog PDU must be received");
					try {
						this.fireTCAbortProvider(tcBeginIndication.getDialog(), MAPProviderAbortReason.abnormalDialogue, null, false);
					} catch (MAPException e) {
						loger.error("Error while firing TC-U-ABORT. ", e);
					}
					return;
				}

				mapOpenInfoImpl.decodeAll(ais);

				destReference = mapOpenInfoImpl.getDestReference();
				origReference = mapOpenInfoImpl.getOrigReference();
				extensionContainer = mapOpenInfoImpl.getExtensionContainer();
				eriStyle = mapOpenInfoImpl.getEriStyle();
				eriImsi = mapOpenInfoImpl.getEriImsi();
				eriVlrNo = mapOpenInfoImpl.getEriVlrNo();
			} catch (AsnException e) {
				e.printStackTrace();
				loger.error("AsnException when parsing MAP-OPEN Pdu: " + e.getMessage(), e);
				try {
					this.fireTCAbortProvider(tcBeginIndication.getDialog(), MAPProviderAbortReason.abnormalDialogue, null, false);
				} catch (MAPException e1) {
					loger.error("Error while firing TC-U-ABORT. ", e1);
				}
				return;
			} catch (IOException e) {
				e.printStackTrace();
				loger.error("IOException when parsing MAP-OPEN Pdu: " + e.getMessage());
				try {
					this.fireTCAbortProvider(tcBeginIndication.getDialog(), MAPProviderAbortReason.abnormalDialogue, null, false);
				} catch (MAPException e1) {
					loger.error("Error while firing TC-U-ABORT. ", e1);
				}
				return;
			} catch (MAPParsingComponentException e) {
				e.printStackTrace();
				loger.error("MAPException when parsing MAP-OPEN Pdu: " + e.getMessage());
				try {
					this.fireTCAbortProvider(tcBeginIndication.getDialog(), MAPProviderAbortReason.abnormalDialogue, null, false);
				} catch (MAPException e1) {
					loger.error("Error while firing TC-U-ABORT. ", e1);
				}
				return;
			}
		}

		// Selecting the MAP service that can perform the ApplicationContext
		if (perfSer == null) {
			for (MAPServiceBase ser : this.mapServices) {

				ServingCheckData chkRes = ser.isServingService(mapAppCtx);
				switch (chkRes.getResult()) {
				case AC_Serving:
					perfSer = ser;
					break;

				case AC_VersionIncorrect:
					try {
						this.fireTCAbortACNNotSupported(tcBeginIndication.getDialog(), null, chkRes.getAlternativeApplicationContext(), false);
					} catch (MAPException e1) {
						loger.error("Error while firing TC-U-ABORT. ", e1);
					}
					break;
				}

				if (perfSer != null)
					break;
			}
		}

		// No MAPService can accept the received ApplicationContextName
		if (perfSer == null) {
			StringBuffer s = new StringBuffer();
			s.append("Unsupported ApplicationContextName is received: ");
			for (long l : acn.getOid()) {
				s.append(l).append(", ");
			}

			loger.error(s.toString());
			try {
				this.fireTCAbortACNNotSupported(tcBeginIndication.getDialog(), null, null, false);
			} catch (MAPException e1) {
				loger.error("Error while firing TC-U-ABORT. ", e1);
			}

			return;
		}

		// MAPService is not activated
		if (!perfSer.isActivated()) {
			StringBuffer s = new StringBuffer();
			s.append("ApplicationContextName of not activated MAPService is received: ");
			for (long l : acn.getOid()) {
				s.append(l).append(", ");
			}

			try {
				this.fireTCAbortACNNotSupported(tcBeginIndication.getDialog(), null, null, false);
			} catch (MAPException e1) {
				loger.error("Error while firing TC-U-ABORT. ", e1);
			}
		}

		MAPDialogImpl mapDialogImpl = ((MAPServiceBaseImpl) perfSer).createNewDialogIncoming(mapAppCtx, tcBeginIndication.getDialog());
		synchronized (mapDialogImpl) {
			this.addDialog(mapDialogImpl);

			mapDialogImpl.setState(MAPDialogState.INITIAL_RECEIVED);

			if (eriStyle) {
				this.deliverDialogRequestEri(mapDialogImpl, destReference, origReference, eriImsi, eriVlrNo);
			} else {
				this.deliverDialogRequest(mapDialogImpl, destReference, origReference, extensionContainer);
			}
			if (mapDialogImpl.getState() == MAPDialogState.EXPUNGED)
				// The Dialog was aborter or refused
				return;

			// Now let us decode the Components
			if (comps != null) {
				processComponents(mapDialogImpl, comps);
			}

			this.deliverDialogDelimiter(mapDialogImpl);
		}
	}

	public void onTCContinue(TCContinueIndication tcContinueIndication) {

		Dialog tcapDialog = tcContinueIndication.getDialog();

		MAPDialogImpl mapDialogImpl = (MAPDialogImpl) this.getMAPDialog(tcapDialog.getDialogId());

		if (mapDialogImpl == null) {
			// TODO : ABort TCAP?
			loger.error("MAP Dialog not found for Dialog Id " + tcapDialog.getDialogId());
			try {
				this.fireTCAbortProvider(tcapDialog, MAPProviderAbortReason.abnormalDialogue, null, false);
			} catch (MAPException e) {
				loger.error("Error while firing TC-U-ABORT. ", e);
			}
			return;
		}

		synchronized (mapDialogImpl) {
			// Checking the received ApplicationContextName :
			// On receipt of the first TC-CONTINUE indication primitive for
			// a dialogue, the MAP PM shall check the value of the
			// application-context-name parameter. If this value matches the
			// one used in the MAP-OPEN request primitive, the MAP PM shall
			// issue a MAP-OPEN confirm primitive with the result parameter
			// indicating "accepted", then process the following TC
			// component handling indication primitives as described in
			// clause 12.6, and then waits for a request primitive from its
			// user or an indication primitive from TC, otherwise it shall
			// issue a TC-U-ABORT request primitive with a MAP-providerAbort
			// PDU indicating "abnormal dialogue" and a MAP-P-ABORT
			// indication primitive with the "provider-reason" parameter
			// indicating "abnormal dialogue".
			if (mapDialogImpl.getState() == MAPDialogState.INITIAL_SENT) {
				ApplicationContextName acn = tcContinueIndication.getApplicationContextName();

				if (acn == null) {

					// if MAP V1 - no acn included
					if (mapDialogImpl.getApplicationContext().getApplicationContextVersion() != MAPApplicationContextVersion.version1) {

						loger.error(String.format("Received first TC-CONTINUE for MAPDialog=%s. But no application-context-name included", mapDialogImpl));
						// this.dialogs.remove(mapDialogImpl.getDialogId());
						try {
							this.fireTCAbortProvider(tcapDialog, MAPProviderAbortReason.abnormalDialogue, null, mapDialogImpl.getReturnMessageOnError());

						} catch (MAPException e) {
							loger.error("Error while firing TC-U-ABORT. ", e);
						}

						mapDialogImpl.setNormalDialogShutDown();
						this.deliverDialogProviderAbort(mapDialogImpl, MAPAbortProviderReason.AbnormalMAPDialogue, MAPAbortSource.MAPProblem, null);
						mapDialogImpl.setState(MAPDialogState.EXPUNGED);

						return;
					}
				} else {
					
					MAPApplicationContext mapAcn = MAPApplicationContext.getInstance(acn.getOid());
					if (mapAcn == null || !mapAcn.equals(mapDialogImpl.getApplicationContext())) {
						loger.error(String.format("Received first TC-CONTINUE. MAPDialog=%s. But MAPApplicationContext=%s", mapDialogImpl, mapAcn));

						// this.dialogs.remove(mapDialogImpl.getDialogId());
						try {
							this.fireTCAbortProvider(tcapDialog, MAPProviderAbortReason.abnormalDialogue, null, mapDialogImpl.getReturnMessageOnError());
						} catch (MAPException e) {
							loger.error("Error while firing TC-U-ABORT. ", e);
						}

						mapDialogImpl.setNormalDialogShutDown();
						this.deliverDialogProviderAbort(mapDialogImpl, MAPAbortProviderReason.AbnormalMAPDialogue, MAPAbortSource.MAPProblem, null);
						mapDialogImpl.setState(MAPDialogState.EXPUNGED);

						return;
					}
				}

				MAPExtensionContainer extensionContainer = null;

				// Parse MapAcceptInfo if it exists - we ignore all errors this
				UserInformation userInfo = tcContinueIndication.getUserInformation();
				if (userInfo != null) {
					MAPAcceptInfoImpl mapAcceptInfoImpl = new MAPAcceptInfoImpl();

					if (userInfo.isOid()) {
						long[] oid = userInfo.getOidValue();
						MAPDialogueAS mapDialAs = MAPDialogueAS.getInstance(oid);

						if (mapDialAs != null && userInfo.isAsn()) {
							try {
								byte[] asnData = userInfo.getEncodeType();

								AsnInputStream ais = new AsnInputStream(asnData);

								int tag = ais.readTag();

								// It should be MAP_ACCEPT Tag
								if (tag == MAPAcceptInfoImpl.MAP_ACCEPT_INFO_TAG) {
									mapAcceptInfoImpl.decodeAll(ais);

									extensionContainer = mapAcceptInfoImpl.getExtensionContainer();
								}
							} catch (AsnException e) {
								e.printStackTrace();
								loger.error("AsnException when parsing MAP-ACCEPT Pdu: " + e.getMessage(), e);
								return;
							} catch (IOException e) {
								e.printStackTrace();
								loger.error("IOException when parsing MAP-ACCEPT Pdu: " + e.getMessage(), e);
							} catch (MAPParsingComponentException e) {
								e.printStackTrace();
								loger.error("MAPException when parsing MAP-ACCEPT Pdu: " + e.getMessage(), e);
							}
						}
					}
				}

				// Fire MAPAcceptInfo
				mapDialogImpl.setState(MAPDialogState.ACTIVE);
				this.deliverDialogAccept(mapDialogImpl, extensionContainer);

				if (mapDialogImpl.getState() == MAPDialogState.EXPUNGED)
					// The Dialog was aborter
					return;
			}

			// Now let us decode the Components
			if (mapDialogImpl.getState() == MAPDialogState.INITIAL_SENT || mapDialogImpl.getState() == MAPDialogState.ACTIVE) {
				Component[] comps = tcContinueIndication.getComponents();
				if (comps != null) {
					processComponents(mapDialogImpl, comps);
				}
			} else {
				// This should never happen
				loger.error(String.format("Received TC-CONTINUE. MAPDialog=%s. But state is neither InitialSent or Active", mapDialogImpl));
			}

			this.deliverDialogDelimiter(mapDialogImpl);
		}
	}

	public void onTCEnd(TCEndIndication tcEndIndication) {

		Dialog tcapDialog = tcEndIndication.getDialog();

		MAPDialogImpl mapDialogImpl = (MAPDialogImpl) this.getMAPDialog(tcapDialog.getDialogId());

		if (mapDialogImpl == null) {
			loger.error("MAP Dialog not found for Dialog Id " + tcapDialog.getDialogId());
			return;
		}

		synchronized (mapDialogImpl) {
			if (mapDialogImpl.getState() == MAPDialogState.INITIAL_SENT) {
				// On receipt of a TC-END indication primitive in the dialogue
				// initiated state, the MAP PM shall check the value of the
				// application-context-name parameter. If this value does not
				// match
				// the one used in the MAPOPEN request primitive, the MAP PM
				// shall
				// discard any following component handling primitive and shall
				// issue a MAP-P-ABORT indication primitive with the
				// "provider-reason" parameter indicating "abnormal dialogue".
				ApplicationContextName acn = tcEndIndication.getApplicationContextName();

				if (acn == null) {
					
					// if MAP V1 - no acn included
					if (mapDialogImpl.getApplicationContext().getApplicationContextVersion() != MAPApplicationContextVersion.version1) {
						
						// for MAP version >= 2 - accepts only if only ERROR & REJECT components are present
						boolean onlyErrorReject = false;
						for (Component c : tcEndIndication.getComponents()) {
							if (c.getType() != ComponentType.ReturnError && c.getType() != ComponentType.Reject) {
								onlyErrorReject = true;
								break;
							}
						}
						if (onlyErrorReject) {
							loger.error(String.format("Received first TC-END for MAPDialog=%s. But no application-context-name included", mapDialogImpl));

							mapDialogImpl.setNormalDialogShutDown();
							this.deliverDialogProviderAbort(mapDialogImpl, MAPAbortProviderReason.AbnormalMAPDialogue, MAPAbortSource.MAPProblem, null);
							mapDialogImpl.setState(MAPDialogState.EXPUNGED);

							return;
						}
					}
				} else {
					
					MAPApplicationContext mapAcn = MAPApplicationContext.getInstance(acn.getOid());

					if (mapAcn == null || !mapAcn.equals(mapDialogImpl.getApplicationContext())) {
						loger.error(String.format("Received first TC-END. MAPDialog=%s. But MAPApplicationContext=%s", mapDialogImpl, mapAcn));

						mapDialogImpl.setNormalDialogShutDown();
						this.deliverDialogProviderAbort(mapDialogImpl, MAPAbortProviderReason.AbnormalMAPDialogue, MAPAbortSource.MAPProblem, null);
						mapDialogImpl.setState(MAPDialogState.EXPUNGED);

						return;
					}
				}

				// Otherwise it shall issue a MAP-OPEN confirm primitive with
				// the
				// result
				// parameter set to "accepted" and process the following TC
				// component
				// handling indication primitives as described in clause 12.6;

				// Fire MAPAcceptInfo
				mapDialogImpl.setState(MAPDialogState.ACTIVE);

				MAPExtensionContainer extensionContainer = null;
				// Parse MapAcceptInfo or MapCloseInfo if it exists - we ignore
				// all errors this
				UserInformation userInfo = tcEndIndication.getUserInformation();
				if (userInfo != null) {

					if (userInfo.isOid()) {
						long[] oid = userInfo.getOidValue();
						MAPDialogueAS mapDialAs = MAPDialogueAS.getInstance(oid);

						if (mapDialAs != null && userInfo.isAsn()) {
							try {
								byte[] asnData = userInfo.getEncodeType();

								AsnInputStream ais = new AsnInputStream(asnData);

								int tag = ais.readTag();

								// It should be MAP_ACCEPT Tag or MAP_CLOSE Tag
								if (tag == MAPAcceptInfoImpl.MAP_ACCEPT_INFO_TAG) {
									MAPAcceptInfoImpl mapAcceptInfoImpl = new MAPAcceptInfoImpl();
									mapAcceptInfoImpl.decodeAll(ais);

									extensionContainer = mapAcceptInfoImpl.getExtensionContainer();
								}
								if (tag == MAPCloseInfoImpl.MAP_CLOSE_INFO_TAG) {
									MAPCloseInfoImpl mapCloseInfoImpl = new MAPCloseInfoImpl();
									mapCloseInfoImpl.decodeAll(ais);

									extensionContainer = mapCloseInfoImpl.getExtensionContainer();
								}
							} catch (AsnException e) {
								e.printStackTrace();
								loger.error("AsnException when parsing MAP-ACCEPT/MAP-CLOSE Pdu: " + e.getMessage(), e);
								return;
							} catch (IOException e) {
								e.printStackTrace();
								loger.error("IOException when parsing MAP-ACCEPT/MAP-CLOSE Pdu: " + e.getMessage(), e);
							} catch (MAPParsingComponentException e) {
								e.printStackTrace();
								loger.error("MAPException when parsing MAP-ACCEPT/MAP-CLOSE Pdu: " + e.getMessage(), e);
							}
						}
					}
				}

				this.deliverDialogAccept(mapDialogImpl, extensionContainer);
				if (mapDialogImpl.getState() == MAPDialogState.EXPUNGED)
					// The Dialog was aborter
					return;
			}

			// Now let us decode the Components
			Component[] comps = tcEndIndication.getComponents();
			if (comps != null) {
				processComponents(mapDialogImpl, comps);
			}

			mapDialogImpl.setNormalDialogShutDown();
			this.deliverDialogClose(mapDialogImpl);
			mapDialogImpl.setState(MAPDialogState.EXPUNGED);
		}
	}

	public void onTCUni(TCUniIndication arg0) {
		// TODO Throw Exception or Ignore? This should never happen for MAP

	}

	@Override
	public void onInvokeTimeout(Invoke invoke) {

		MAPDialogImpl mapDialogImpl = (MAPDialogImpl) this.getMAPDialog(((InvokeImpl) invoke).getDialog().getDialogId());

		if (mapDialogImpl != null) {
			synchronized (mapDialogImpl) {
				if (mapDialogImpl.getState() != MAPDialogState.EXPUNGED && !mapDialogImpl.getNormalDialogShutDown()) {

					// Getting the MAP Service that serves the MAP Dialog
					MAPServiceBaseImpl perfSer = (MAPServiceBaseImpl)mapDialogImpl.getService();
					
					// Check if the InvokeTimeout in this situation is normal (may be for a class 2,3,4 components)
					// TODO: ................................
					
					perfSer.deliverInvokeTimeout(mapDialogImpl, invoke);
				}
			}
		}
	}

	@Override
	public void onDialogTimeout(Dialog tcapDialog) {
		
		MAPDialogImpl mapDialogImpl = (MAPDialogImpl) this.getMAPDialog(tcapDialog.getDialogId());

		if (mapDialogImpl != null) {
			synchronized (mapDialogImpl) {
				if (mapDialogImpl.getState() != MAPDialogState.EXPUNGED && !mapDialogImpl.getNormalDialogShutDown()) {

					this.deliverDialogTimeout(mapDialogImpl);
				}
			}
		}
	}

	@Override
	public void onDialogReleased(Dialog tcapDialog) {

		MAPDialogImpl mapDialogImpl = (MAPDialogImpl) this.getMAPDialog(tcapDialog.getDialogId());

		if (mapDialogImpl != null) {
			synchronized (mapDialogImpl) {
				if (mapDialogImpl.getState() != MAPDialogState.EXPUNGED && !mapDialogImpl.getNormalDialogShutDown()) {

					// TCAP Dialog is destroyed when MapDialog is alive and not shutting down
					mapDialogImpl.setNormalDialogShutDown();
					this.deliverDialogProviderAbort(mapDialogImpl, MAPAbortProviderReason.ProviderMalfunction, MAPAbortSource.TCProblem, null);

					mapDialogImpl.setState(MAPDialogState.EXPUNGED);
				}
			}
		}
	}

	public void onTCPAbort(TCPAbortIndication tcPAbortIndication) {
		Dialog tcapDialog = tcPAbortIndication.getDialog();

		MAPDialogImpl mapDialogImpl = (MAPDialogImpl) this.getMAPDialog(tcapDialog.getDialogId());

		if (mapDialogImpl == null) {
			loger.error("MAP Dialog not found for Dialog Id " + tcapDialog.getDialogId());
			return;
		}

		synchronized (mapDialogImpl) {
			PAbortCauseType pAbortCause = tcPAbortIndication.getPAbortCause();
			MAPAbortProviderReason abortProviderReason = MAPAbortProviderReason.ProviderMalfunction;
			MAPAbortSource abortSource = MAPAbortSource.TCProblem;

			// Table 16.1/1: Mapping of P-Abort cause in TC-P-ABORT indication
			// on to provider-reason in MAP-P-ABORT indication
			// TC P-Abort cause MAP provider-reason
			// unrecognised message type provider malfunction
			// unrecognised transaction Id supporting dialogue released
			// badlyFormattedTransactionPortion provider malfunction
			// incorrectTransactionPortion provider malfunction (note)
			// resourceLimitation resource limitation
			// abnormalDialogue provider malfunction
			// noCommonDialoguePortion version incompatibility
			// NOTE: Or version incompatibility in the dialogue initiated phase.

			switch (pAbortCause) {
			case UnrecognizedMessageType:
			case BadlyFormattedTxPortion:
				abortProviderReason = MAPAbortProviderReason.ProviderMalfunction;
				break;
			case UnrecognizedTxID:
				abortProviderReason = MAPAbortProviderReason.SupportingDialogueTransactionReleased;
				break;
			case IncorrectTxPortion:
				if (mapDialogImpl.getState() == MAPDialogState.INITIAL_SENT) {
					abortProviderReason = MAPAbortProviderReason.VersionIncompatibility;
				} else
					abortProviderReason = MAPAbortProviderReason.ProviderMalfunction;
				break;
			case ResourceLimitation:
				abortProviderReason = MAPAbortProviderReason.ResourceLimitation;
				break;
			}

			mapDialogImpl.setNormalDialogShutDown();
			if (abortProviderReason == MAPAbortProviderReason.VersionIncompatibility)
				// On receipt of a TC-P-ABORT indication primitive in the
				// "Dialogue Initiated" state with a P-abort parameter
				// indicating "Incorrect Transaction Portion", the MAP PM shall
				// issue a MAP-OPEN confirm primitive with
				// the result parameter indicating "Dialogue Refused" and the
				// refuse
				// reason parameter indicating "Potential
				// Version Incompatibility"."
				this.deliverDialogReject(mapDialogImpl, MAPRefuseReason.PotentialVersionIncompatibility, null, null, null);
			else
				this.deliverDialogProviderAbort(mapDialogImpl, abortProviderReason, abortSource, null);

			mapDialogImpl.setState(MAPDialogState.EXPUNGED);
		}
	}

	private enum ParsePduResult {
		NoUserInfo, BadUserInfo, MapRefuse, MapUserAbort, MapProviderAbort;
	}

	public void onTCUserAbort(TCUserAbortIndication tcUserAbortIndication) {
		Dialog tcapDialog = tcUserAbortIndication.getDialog();

		MAPDialogImpl mapDialogImpl = (MAPDialogImpl) this.getMAPDialog(tcapDialog.getDialogId());

		if (mapDialogImpl == null) {
			loger.error("MAP Dialog not found for Dialog Id " + tcapDialog.getDialogId());
			return;
		}

		synchronized (mapDialogImpl) {
			// Trying to parse an userInfo APDU if it exists
			UserInformation userInfo = tcUserAbortIndication.getUserInformation();
			ParsePduResult parsePduResult = ParsePduResult.NoUserInfo;
			MAPRefuseReason mapRefuseReason = MAPRefuseReason.NoReasonGiven;
			MAPUserAbortChoice mapUserAbortChoice = null;
			MAPProviderAbortReason mapProviderAbortReason = null;
			MAPAbortProviderReason abortProviderReason = MAPAbortProviderReason.AbnormalMAPDialogue;
			MAPExtensionContainer extensionContainer = null;

			if (userInfo != null) {
				// Checking userInfo.Oid==MAP_DialogueAS
				if (!userInfo.isOid()) {
					loger.error("When parsing TCUserAbortIndication indication: userInfo.isOid() check failed");
					parsePduResult = ParsePduResult.BadUserInfo;
				} else {

					long[] oid = userInfo.getOidValue();
					MAPDialogueAS mapDialAs = MAPDialogueAS.getInstance(oid);
					if (mapDialAs == null) {
						loger.error("When parsing TCUserAbortIndication indication: userInfo.getOidValue() must be userInfoMAPDialogueAS.MAP_DialogueAS");
						parsePduResult = ParsePduResult.BadUserInfo;
					} else if (!userInfo.isAsn()) {

						loger.error("When parsing TCUserAbortIndication indication: userInfo.isAsn() check failed");
						parsePduResult = ParsePduResult.BadUserInfo;
					} else {

						try {
							byte[] asnData = userInfo.getEncodeType();

							AsnInputStream ais = new AsnInputStream(asnData);

							int tag = ais.readTag();

							switch (tag) {
							case MAPRefuseInfoImpl.MAP_REFUSE_INFO_TAG:
								// On receipt of a TC-U-ABORT indication
								// primitive in the
								// "Dialogue Initiated" state with an
								// abort-reason
								// parameter indicating "User Specific" and a
								// MAP-Refuse PDU
								// included as user information, the MAP PM
								// shall issue a MAP-OPEN confirm primitive with
								// the result
								// set to refused and the refuse reason set as
								// received in the MAP Refuse PDU.
								MAPRefuseInfoImpl mapRefuseInfoImpl = new MAPRefuseInfoImpl();
								mapRefuseInfoImpl.decodeAll(ais);

								switch (mapRefuseInfoImpl.getReason()) {
								case invalidOriginatingReference:
									mapRefuseReason = MAPRefuseReason.InvalidOriginatingReference;
									break;
								case invalidDestinationReference:
									mapRefuseReason = MAPRefuseReason.InvalidDestinationReference;
									break;
								}

								extensionContainer = mapRefuseInfoImpl.getExtensionContainer();
								parsePduResult = ParsePduResult.MapRefuse;
								break;

							case MAPUserAbortInfoImpl.MAP_USER_ABORT_INFO_TAG:
								MAPUserAbortInfoImpl mapUserAbortInfoImpl = new MAPUserAbortInfoImpl();
								mapUserAbortInfoImpl.decodeAll(ais);

								mapUserAbortChoice = mapUserAbortInfoImpl.getMAPUserAbortChoice();
								extensionContainer = mapUserAbortInfoImpl.getExtensionContainer();
								parsePduResult = ParsePduResult.MapUserAbort;
								break;

							case MAPProviderAbortInfoImpl.MAP_PROVIDER_ABORT_INFO_TAG:
								MAPProviderAbortInfoImpl mapProviderAbortInfoImpl = new MAPProviderAbortInfoImpl();
								mapProviderAbortInfoImpl.decodeAll(ais);

								mapProviderAbortReason = mapProviderAbortInfoImpl.getMAPProviderAbortReason();
								switch (mapProviderAbortReason) {
								case abnormalDialogue:
									abortProviderReason = MAPAbortProviderReason.AbnormalMAPDialogue;
									break;
								case invalidPDU:
									abortProviderReason = MAPAbortProviderReason.InvalidPDU;
									break;
								}

								extensionContainer = mapProviderAbortInfoImpl.getExtensionContainer();
								parsePduResult = ParsePduResult.MapProviderAbort;
								break;

							default:
								loger.error("When parsing TCUserAbortIndication indication: userInfogetEncodeType().Tag must be either MAP_REFUSE_INFO_TAG or MAP_USER_ABORT_INFO_TAG or MAP_PROVIDER_ABORT_INFO_TAG");
								parsePduResult = ParsePduResult.BadUserInfo;
								break;
							}

						} catch (AsnException e) {
							loger.error("When parsing TCUserAbortIndication indication: AsnException" + e.getMessage(), e);
							parsePduResult = ParsePduResult.BadUserInfo;
						} catch (IOException e) {
							loger.error("When parsing TCUserAbortIndication indication: IOException" + e.getMessage(), e);
							parsePduResult = ParsePduResult.BadUserInfo;
						} catch (MAPParsingComponentException e) {
							loger.error("When parsing TCUserAbortIndication indication: MAPParsingComponentException" + e.getMessage(), e);
							parsePduResult = ParsePduResult.BadUserInfo;
						}
					}
				}
			}

			// special cases: AareApdu + ApplicationContextNotSupported or
			// NoCommonDialogPortion
			if (tcUserAbortIndication.IsAareApdu()) {
				ResultSourceDiagnostic resultSourceDiagnostic = tcUserAbortIndication.getResultSourceDiagnostic();
				if (resultSourceDiagnostic != null) {
					// ACN_Not_Supported
					if (resultSourceDiagnostic.getDialogServiceUserType() == DialogServiceUserType.AcnNotSupported) {
						if (mapDialogImpl.getState() == MAPDialogState.INITIAL_SENT) {
							mapDialogImpl.setNormalDialogShutDown();
							this.deliverDialogReject(mapDialogImpl, MAPRefuseReason.ApplicationContextNotSupported, null,
									tcUserAbortIndication.getApplicationContextName(), extensionContainer);

							mapDialogImpl.setState(MAPDialogState.EXPUNGED);
							return;
						} else
							parsePduResult = ParsePduResult.BadUserInfo;

					} else if (resultSourceDiagnostic.getDialogServiceProviderType() == DialogServiceProviderType.NoCommonDialogPortion) {
						if (mapDialogImpl.getState() == MAPDialogState.INITIAL_SENT) {
							// NoCommonDialogPortion
							mapDialogImpl.setNormalDialogShutDown();
							this.deliverDialogReject(mapDialogImpl, MAPRefuseReason.PotentialVersionIncompatibility, null, null, null);

							mapDialogImpl.setState(MAPDialogState.EXPUNGED);
							return;
						} else
							parsePduResult = ParsePduResult.BadUserInfo;

					}
				}
			}

			mapDialogImpl.setNormalDialogShutDown();
			switch (parsePduResult) {
			case NoUserInfo:
				// Neither ABRT nor AARE APDU presents

				// On receipt of a TC-U-ABORT indication primitive in the
				// "Dialogue Initiated" state with an abort-reason
				// parameter indicating "User Specific" and without user
				// information, the MAP PM shall issue a MAP-OPEN
				// confirm primitive with the result parameter indicating
				// "Dialogue Refused" and the refuse-reason
				// parameter indicating "Potential Version Incompatibility".

				if (mapDialogImpl.getState() == MAPDialogState.INITIAL_SENT)
					this.deliverDialogReject(mapDialogImpl, MAPRefuseReason.PotentialVersionIncompatibility, null, null, null);
				else
					this.deliverDialogProviderAbort(mapDialogImpl, MAPAbortProviderReason.AbnormalMAPDialogue, MAPAbortSource.MAPProblem, null);
				break;

			case BadUserInfo:
				this.deliverDialogProviderAbort(mapDialogImpl, MAPAbortProviderReason.AbnormalMAPDialogue, MAPAbortSource.MAPProblem, null);
				break;

			case MapRefuse:
				if (mapDialogImpl.getState() == MAPDialogState.INITIAL_SENT) {
					// On receipt of a TC-U-ABORT indication primitive in the
					// "Dialogue Initiated" state with an abort-reason
					// parameter indicating "User Specific" and a MAP-Refuse PDU
					// included as user information, the MAP PM
					// shall issue a MAP-OPEN confirm primitive with the result
					// set to refused and the refuse reason set as
					// received in the MAP Refuse PDU.
					this.deliverDialogReject(mapDialogImpl, mapRefuseReason, null, null, extensionContainer);
				} else {
					// MAPRefuseInfo in a wrong Dialog state
					this.deliverDialogProviderAbort(mapDialogImpl, MAPAbortProviderReason.AbnormalMAPDialogue, MAPAbortSource.MAPProblem, null);
				}
				break;

			case MapUserAbort:
				this.deliverDialogUserAbort(mapDialogImpl, mapUserAbortChoice, extensionContainer);
				break;

			case MapProviderAbort:
				this.deliverDialogProviderAbort(mapDialogImpl, abortProviderReason, MAPAbortSource.MAPProblem, extensionContainer);
				break;
			}

			mapDialogImpl.setState(MAPDialogState.EXPUNGED);
		}
	}

	/**
	 * private service methods
	 */
	private void processComponents(MAPDialogImpl mapDialogImpl, Component[] components) {

		// Getting the MAP Service that serves the MAP Dialog
		MAPServiceBaseImpl perfSer = (MAPServiceBaseImpl)mapDialogImpl.getService();

		// FIXME: Amit DOUBLE CHECK!
		// Now let us decode the Components
		for (Component c : components) {

			try {
				ComponentType compType = c.getType();

				Long invokeId = c.getInvokeId();

				Parameter parameter;
				OperationCode oc;
				Long linkedId = 0L;
				
				switch (compType) {
				case Invoke: {
					Invoke comp = (Invoke) c;
					oc = comp.getOperationCode();
					parameter = comp.getParameter();
					linkedId = comp.getLinkedId();
					
					// Checking if the invokeId is not duplicated
					if (!mapDialogImpl.addIncomingInvokeId(invokeId)) {
						this.deliverDialogNotice(mapDialogImpl, MAPNoticeProblemDiagnostic.AbnormalEventReceivedFromThePeer);
						
						Problem problem = this.getTCAPProvider().getComponentPrimitiveFactory().createProblem(ProblemType.Invoke);
						problem.setInvokeProblemType(InvokeProblemType.DuplicateInvokeID);
						mapDialogImpl.sendRejectComponent(null, problem);

						return;
					}
					
					if (linkedId != null) {
						// linkedId exists Checking if the linkedId exists
						if (!mapDialogImpl.checkIncomingInvokeIdExists(linkedId)) {
							this.deliverDialogNotice(mapDialogImpl, MAPNoticeProblemDiagnostic.AbnormalEventReceivedFromThePeer);

							Problem problem = this.getTCAPProvider().getComponentPrimitiveFactory().createProblem(ProblemType.Invoke);
							problem.setInvokeProblemType(InvokeProblemType.UnrechognizedLinkedID);
							mapDialogImpl.sendRejectComponent(invokeId, problem);

							return;
						}
					}
				}
					break;

				case ReturnResult: {
					ReturnResult comp = (ReturnResult) c;
					oc = comp.getOperationCode();
					parameter = comp.getParameter();
				}
					break;

				case ReturnResultLast: {
					ReturnResultLast comp = (ReturnResultLast) c;
					oc = comp.getOperationCode();
					parameter = comp.getParameter();
				}
					break;

				case ReturnError: {
					ReturnError comp = (ReturnError) c;
					
					long errorCode = 0;
					if (comp.getErrorCode() != null && comp.getErrorCode().getErrorType() == ErrorCodeType.Local)
						errorCode = comp.getErrorCode().getLocalErrorCode();
					if (errorCode < MAPErrorCode.minimalCodeValue || errorCode > MAPErrorCode.maximumCodeValue) {
						// Not Local error code and not MAP error code received
						perfSer.deliverProviderErrorComponent(mapDialogImpl, invokeId, MAPProviderError.InvalidResponseReceived);

						Problem problem = this.getTCAPProvider().getComponentPrimitiveFactory().createProblem(ProblemType.ReturnError);
						problem.setReturnErrorProblemType(ReturnErrorProblemType.UnrecognizedError);
						mapDialogImpl.sendRejectComponent(invokeId, problem);
						
						return;
					}
					
					MAPErrorMessage msgErr = this.mapErrorMessageFactory.createMessageFromErrorCode(errorCode);
					try {
						//msgErr.decodeParameter(comp.getParameter());
						Parameter p = comp.getParameter();
						if (p != null && p.getData() != null) {
							byte[] data = p.getData();
							AsnInputStream ais = new AsnInputStream(data, p.getTagClass(), p.isPrimitive(), p.getTag());
							((MAPErrorMessageImpl)msgErr).decodeData(ais, data.length);
						}
					} catch ( MAPParsingComponentException e) {
						// Failed when parsing the component - send TC-U-REJECT
						perfSer.deliverProviderErrorComponent(mapDialogImpl, invokeId, MAPProviderError.InvalidResponseReceived);

						Problem problem = this.getTCAPProvider().getComponentPrimitiveFactory().createProblem(ProblemType.ReturnError);
						problem.setReturnErrorProblemType(ReturnErrorProblemType.MistypedParameter);
						mapDialogImpl.sendRejectComponent(invokeId, problem);

						return;
					}
					perfSer.deliverErrorComponent(mapDialogImpl, comp.getInvokeId(), msgErr);
					
					return;
				}

				case Reject: {
					Reject comp = (Reject) c;
					perfSer.deliverRejectComponent(mapDialogImpl, comp.getInvokeId(), comp.getProblem());
					
					return;
				}
				
				default:
					return;
				}
				
				try {
					
					perfSer.processComponent(compType, oc, parameter, mapDialogImpl, invokeId, linkedId);
					
				} catch (MAPParsingComponentException e) {
					
					loger.error("MAPParsingComponentException when parsing components: " + e.getReason().toString() + " - " + e.getMessage(), e);
					
					switch (e.getReason()) {
					case UnrecognizedOperation:
						// Component does not supported - send TC-U-REJECT
						if (compType == ComponentType.Invoke) {
							this.deliverDialogNotice(mapDialogImpl, MAPNoticeProblemDiagnostic.AbnormalEventReceivedFromThePeer);
							
							Problem problem = this.getTCAPProvider().getComponentPrimitiveFactory().createProblem(ProblemType.Invoke);
							problem.setInvokeProblemType(InvokeProblemType.UnrecognizedOperation);
							mapDialogImpl.sendRejectComponent(invokeId, problem);
						} else {
							perfSer.deliverProviderErrorComponent(mapDialogImpl, invokeId, MAPProviderError.InvalidResponseReceived);
							
							Problem problem = this.getTCAPProvider().getComponentPrimitiveFactory().createProblem(ProblemType.ReturnResult);
							problem.setReturnResultProblemType(ReturnResultProblemType.MistypedParameter);
							mapDialogImpl.sendRejectComponent(invokeId, problem);
						}
						break;

					case MistypedParameter:
						// Failed when parsing the component - send TC-U-REJECT
						if (compType == ComponentType.Invoke) {
							this.deliverDialogNotice(mapDialogImpl, MAPNoticeProblemDiagnostic.AbnormalEventReceivedFromThePeer);
							
							Problem problem = this.getTCAPProvider().getComponentPrimitiveFactory().createProblem(ProblemType.Invoke);
							problem.setInvokeProblemType(InvokeProblemType.MistypedParameter);
							mapDialogImpl.sendRejectComponent(invokeId, problem);
						} else {
							perfSer.deliverProviderErrorComponent(mapDialogImpl, invokeId, MAPProviderError.InvalidResponseReceived);
							
							Problem problem = this.getTCAPProvider().getComponentPrimitiveFactory().createProblem(ProblemType.ReturnResult);
							problem.setReturnResultProblemType(ReturnResultProblemType.MistypedParameter);
							mapDialogImpl.sendRejectComponent(invokeId, problem);
						}
						break;

					case LinkedResponseUnexpected:
						// Failed when parsing the component - send TC-U-REJECT
						if (compType == ComponentType.Invoke) {
							this.deliverDialogNotice(mapDialogImpl, MAPNoticeProblemDiagnostic.AbnormalEventReceivedFromThePeer);
							
							Problem problem = this.getTCAPProvider().getComponentPrimitiveFactory().createProblem(ProblemType.Invoke);
							problem.setInvokeProblemType(InvokeProblemType.LinkedResponseUnexpected);
							mapDialogImpl.sendRejectComponent(invokeId, problem);
						}
						break;

					case UnexpectedLinkedOperation:
						// Failed when parsing the component - send TC-U-REJECT
						if (compType == ComponentType.Invoke) {
							this.deliverDialogNotice(mapDialogImpl, MAPNoticeProblemDiagnostic.AbnormalEventReceivedFromThePeer);
							
							Problem problem = this.getTCAPProvider().getComponentPrimitiveFactory().createProblem(ProblemType.Invoke);
							problem.setInvokeProblemType(InvokeProblemType.UnexpectedLinkedOperation);
							mapDialogImpl.sendRejectComponent(invokeId, problem);
						}
						break;
					}

				}
			} catch (MAPException e) {
				loger.error("Error sending the RejectComponent: " + e.getMessage(), e);
			}
			
		} 

	}

	@Override
	public void onTCNotice(TCNoticeIndication ind) {

		Dialog tcapDialog = ind.getDialog();
		if (tcapDialog == null) {
			// no existent Dialog for TC-NOTICE
			return;
		}

		MAPDialogImpl mapDialogImpl = (MAPDialogImpl) this.getMAPDialog(tcapDialog.getDialogId());

		if (mapDialogImpl == null) {
			loger.error("MAP Dialog not found for Dialog Id " + tcapDialog.getDialogId());
			return;
		}

		synchronized (mapDialogImpl) {
			if (mapDialogImpl.getState() == MAPDialogState.INITIAL_RECEIVED) {
				// If a TC-NOTICE indication primitive is received before the
				// dialogue has been confirmed (i.e. no backward message is
				// received by the dialogue initiator node), the MAP PM shall
				// issue a MAP-OP EN Cnf primitive with the result parameter
				// indicating Refused and a refuse reason Remote node not
				// reachable”.
				mapDialogImpl.setNormalDialogShutDown();
				this.deliverDialogReject(mapDialogImpl, MAPRefuseReason.RemoteNodeNotReachable, null, null, null);
				mapDialogImpl.setState(MAPDialogState.EXPUNGED);
			} else if (mapDialogImpl.getState() == MAPDialogState.ACTIVE) {
				// If a TC-NOTICE indication primitive is received after the
				// dialogue has been confirmed, the MAP PM shall issue a
				// MAP-NOTICE indication to the user, with a problem diagnostic
				// indicating "message cannot be delivered to the peer".
				this.deliverDialogNotice(mapDialogImpl, MAPNoticeProblemDiagnostic.MessageCannotBeDeliveredToThePeer);
			}
		}
	}

	private void deliverDialogDelimiter(MAPDialog mapDialog) {
		for (MAPDialogListener listener : this.dialogListeners) {
			listener.onDialogDelimiter(mapDialog);
		}
	}

	private void deliverDialogRequest(MAPDialog mapDialog, AddressString destReference, AddressString origReference, MAPExtensionContainer extensionContainer) {
		for (MAPDialogListener listener : this.dialogListeners) {
			listener.onDialogRequest(mapDialog, destReference, origReference, extensionContainer);
		}
	}

	private void deliverDialogRequestEri(MAPDialog mapDialog, AddressString destReference, AddressString origReference, IMSI eriImsi, AddressString eriVlrNo) {
		for (MAPDialogListener listener : this.dialogListeners) {
			listener.onDialogRequestEricsson(mapDialog, destReference, origReference, eriImsi, eriVlrNo);
		}
	}

	private void deliverDialogAccept(MAPDialog mapDialog, MAPExtensionContainer extensionContainer) {
		for (MAPDialogListener listener : this.dialogListeners) {
			listener.onDialogAccept(mapDialog, extensionContainer);
		}
	}

	private void deliverDialogReject(MAPDialog mapDialog, MAPRefuseReason refuseReason, MAPProviderError providerError,
			ApplicationContextName alternativeApplicationContext, MAPExtensionContainer extensionContainer) {
		for (MAPDialogListener listener : this.dialogListeners) {
			listener.onDialogReject(mapDialog, refuseReason, providerError, alternativeApplicationContext, extensionContainer);
		}
	}

	private void deliverDialogClose(MAPDialog mapDialog) {
		for (MAPDialogListener listener : this.dialogListeners) {
			listener.onDialogClose(mapDialog);
		}
	}

	private void deliverDialogProviderAbort(MAPDialog mapDialog, MAPAbortProviderReason abortProviderReason, MAPAbortSource abortSource,
			MAPExtensionContainer extensionContainer) {
		for (MAPDialogListener listener : this.dialogListeners) {
			listener.onDialogProviderAbort(mapDialog, abortProviderReason, abortSource, extensionContainer);
		}
	}

	private void deliverDialogUserAbort(MAPDialog mapDialog, MAPUserAbortChoice userReason, MAPExtensionContainer extensionContainer) {
		for (MAPDialogListener listener : this.dialogListeners) {
			listener.onDialogUserAbort(mapDialog, userReason, extensionContainer);
		}
	}

	protected void deliverDialogNotice(MAPDialog mapDialog, MAPNoticeProblemDiagnostic noticeProblemDiagnostic) {
		for (MAPDialogListener listener : this.dialogListeners) {
			listener.onDialogNotice(mapDialog, noticeProblemDiagnostic);
		}
	}

	protected void deliverDialogResease(MAPDialog mapDialog) {
		for (MAPDialogListener listener : this.dialogListeners) {
			listener.onDialogRelease(mapDialog);
		}
	}

	protected void deliverDialogTimeout(MAPDialog mapDialog) {
		for (MAPDialogListener listener : this.dialogListeners) {
			listener.onDialogTimeout(mapDialog);
		}
	}

	protected void fireTCBegin(Dialog tcapDialog, ApplicationContextName acn, AddressString destReference, AddressString origReference,
			MAPExtensionContainer mapExtensionContainer, boolean isEriStyle, IMSI imsiEri, AddressString vlrNoEri, boolean returnMessageOnError)
			throws MAPException {

		TCBeginRequest tcBeginReq = encodeTCBegin(tcapDialog, acn, destReference, origReference, mapExtensionContainer, isEriStyle, imsiEri, vlrNoEri);
		if (returnMessageOnError)
			tcBeginReq.setReturnMessageOnError(true);

		try {
			tcapDialog.send(tcBeginReq);
		} catch (TCAPSendException e) {
			throw new MAPException(e.getMessage(), e);
		}

	}

	protected TCBeginRequest encodeTCBegin(Dialog tcapDialog, ApplicationContextName acn, AddressString destReference, AddressString origReference,
			MAPExtensionContainer mapExtensionContainer, boolean eriStyle, IMSI eriImsi, AddressString eriVlrNo) throws MAPException {
		TCBeginRequest tcBeginReq = this.getTCAPProvider().getDialogPrimitiveFactory().createBegin(tcapDialog);

		// we do not set ApplicationContextName if MAP Version 1
		if (MAPApplicationContext.getProtocolVersion(acn.getOid()) > 1)
			tcBeginReq.setApplicationContextName(acn);

		if ((destReference != null || origReference != null || mapExtensionContainer != null || eriStyle)
				&& MAPApplicationContext.getProtocolVersion(acn.getOid()) > 1) {
			MAPOpenInfoImpl mapOpn = new MAPOpenInfoImpl();
			mapOpn.setDestReference(destReference);
			mapOpn.setOrigReference(origReference);
			mapOpn.setExtensionContainer(mapExtensionContainer);
			mapOpn.setEriStyle(eriStyle);
			mapOpn.setEriImsi(eriImsi);
			mapOpn.setEriVlrNo(eriVlrNo);

			AsnOutputStream localasnOs = new AsnOutputStream();
			mapOpn.encodeAll(localasnOs);

			UserInformation userInformation = TcapFactory.createUserInformation();

			userInformation.setOid(true);
			userInformation.setOidValue(MAPDialogueAS.MAP_DialogueAS.getOID());

			userInformation.setAsn(true);
			userInformation.setEncodeType(localasnOs.toByteArray());

			tcBeginReq.setUserInformation(userInformation);
		}
		return tcBeginReq;
	}

	protected void fireTCContinue(Dialog tcapDialog, Boolean sendMapAcceptInfo, ApplicationContextName acn, MAPExtensionContainer mapExtensionContainer,
			boolean returnMessageOnError) throws MAPException {

		TCContinueRequest tcContinueReq = encodeTCContinue(tcapDialog, sendMapAcceptInfo, acn, mapExtensionContainer);
		if (returnMessageOnError)
			tcContinueReq.setReturnMessageOnError(true);

		try {
			tcapDialog.send(tcContinueReq);
		} catch (TCAPSendException e) {
			throw new MAPException(e.getMessage(), e);
		}
	}

	protected TCContinueRequest encodeTCContinue(Dialog tcapDialog, Boolean sendMapAcceptInfo, ApplicationContextName acn,
			MAPExtensionContainer mapExtensionContainer) throws MAPException {
		TCContinueRequest tcContinueReq = this.getTCAPProvider().getDialogPrimitiveFactory().createContinue(tcapDialog);

		// we do not set ApplicationContextName if MAP Version 1
		if (acn != null && MAPApplicationContext.getProtocolVersion(acn.getOid()) > 1)
			tcContinueReq.setApplicationContextName(acn);

		if (sendMapAcceptInfo && mapExtensionContainer != null && MAPApplicationContext.getProtocolVersion(acn.getOid()) > 1) {

			MAPAcceptInfoImpl mapAccept = new MAPAcceptInfoImpl();
			mapAccept.setExtensionContainer(mapExtensionContainer);

			AsnOutputStream localasnOs = new AsnOutputStream();
			mapAccept.encodeAll(localasnOs);

			UserInformation userInformation = TcapFactory.createUserInformation();

			userInformation.setOid(true);
			userInformation.setOidValue(MAPDialogueAS.MAP_DialogueAS.getOID());

			userInformation.setAsn(true);
			userInformation.setEncodeType(localasnOs.toByteArray());

			tcContinueReq.setUserInformation(userInformation);
		}
		return tcContinueReq;
	}

	protected void fireTCEnd(Dialog tcapDialog, Boolean sendMapCloseInfo, boolean prearrangedEnd, ApplicationContextName acn,
			MAPExtensionContainer mapExtensionContainer, boolean returnMessageOnError) throws MAPException {

		TCEndRequest endRequest = encodeTCEnd(tcapDialog, sendMapCloseInfo, prearrangedEnd, acn, mapExtensionContainer);
		if (returnMessageOnError)
			endRequest.setReturnMessageOnError(true);

		try {
			tcapDialog.send(endRequest);
		} catch (TCAPSendException e) {
			throw new MAPException(e.getMessage(), e);
		}
	}

	protected TCEndRequest encodeTCEnd(Dialog tcapDialog, Boolean sendMapCloseInfo, boolean prearrangedEnd, ApplicationContextName acn,
			MAPExtensionContainer mapExtensionContainer) throws MAPException {
		TCEndRequest endRequest = this.getTCAPProvider().getDialogPrimitiveFactory().createEnd(tcapDialog);

		if (!prearrangedEnd) {
			endRequest.setTermination(TerminationType.Basic);
		} else {
			endRequest.setTermination(TerminationType.PreArranged);
		}

		// we do not set ApplicationContextName if MAP Version 1
		if (acn != null && MAPApplicationContext.getProtocolVersion(acn.getOid()) > 1)
			endRequest.setApplicationContextName(acn);

		if (sendMapCloseInfo && mapExtensionContainer != null && MAPApplicationContext.getProtocolVersion(acn.getOid()) > 1) {
			MAPAcceptInfoImpl mapAccept = new MAPAcceptInfoImpl();
			mapAccept.setExtensionContainer(mapExtensionContainer);

			AsnOutputStream localasnOs = new AsnOutputStream();
			mapAccept.encodeAll(localasnOs);

			UserInformation userInformation = TcapFactory.createUserInformation();

			userInformation.setOid(true);
			userInformation.setOidValue(MAPDialogueAS.MAP_DialogueAS.getOID());

			userInformation.setAsn(true);
			userInformation.setEncodeType(localasnOs.toByteArray());

			endRequest.setUserInformation(userInformation);
		}
		return endRequest;
	}

	/**
	 * Issue TC-U-ABORT with the "abort reason" =
	 * "application-content-name-not-supported"
	 * 
	 * @param tcapDialog
	 * @param mapExtensionContainer
	 * @param alternativeApplicationContext
	 * @throws MAPException
	 */
	private void fireTCAbortACNNotSupported(Dialog tcapDialog, MAPExtensionContainer mapExtensionContainer,
			ApplicationContextName alternativeApplicationContext, boolean returnMessageOnError) throws MAPException {

		if (tcapDialog.getApplicationContextName() == null) // MAP V1
			this.fireTCAbortV1(tcapDialog, returnMessageOnError);

		TCUserAbortRequest tcUserAbort = this.getTCAPProvider().getDialogPrimitiveFactory().createUAbort(tcapDialog);

		MAPRefuseInfoImpl mapRefuseInfoImpl = new MAPRefuseInfoImpl();
		mapRefuseInfoImpl.setReason(Reason.noReasonGiven);

		AsnOutputStream localasnOs = new AsnOutputStream();
		mapRefuseInfoImpl.encodeAll(localasnOs);

		UserInformation userInformation = TcapFactory.createUserInformation();

		userInformation.setOid(true);
		userInformation.setOidValue(MAPDialogueAS.MAP_DialogueAS.getOID());

		userInformation.setAsn(true);
		userInformation.setEncodeType(localasnOs.toByteArray());
		if (returnMessageOnError)
			tcUserAbort.setReturnMessageOnError(true);

		if (alternativeApplicationContext != null)
			tcUserAbort.setApplicationContextName(alternativeApplicationContext);
		else
			tcUserAbort.setApplicationContextName(tcapDialog.getApplicationContextName());
		tcUserAbort.setDialogServiceUserType(DialogServiceUserType.AcnNotSupported);
		tcUserAbort.setUserInformation(userInformation);

		try {
			tcapDialog.send(tcUserAbort);
		} catch (TCAPSendException e) {
			throw new MAPException(e.getMessage(), e);
		}
	}

	/**
	 * Issue TC-U-ABORT with the "abort reason" = "dialogue-refused"
	 * 
	 * @param tcapDialog
	 * @param reason
	 * @param mapExtensionContainer
	 * @throws MAPException
	 */
	protected void fireTCAbortRefused(Dialog tcapDialog, Reason reason, MAPExtensionContainer mapExtensionContainer, boolean returnMessageOnError)
			throws MAPException {

		if (tcapDialog.getApplicationContextName() == null) // MAP V1
			this.fireTCAbortV1(tcapDialog, returnMessageOnError);

		TCUserAbortRequest tcUserAbort = this.getTCAPProvider().getDialogPrimitiveFactory().createUAbort(tcapDialog);

		MAPRefuseInfoImpl mapRefuseInfoImpl = new MAPRefuseInfoImpl();
		mapRefuseInfoImpl.setReason(reason);
		mapRefuseInfoImpl.setExtensionContainer(mapExtensionContainer);

		// ApplicationContextName aacn = new ApplicationContextNameImpl();
		// aacn.setOid(new long[] { 3, 4, 5 } );
		// mapRefuseInfoImpl.setAlternativeAcn(aacn);

		AsnOutputStream localasnOs = new AsnOutputStream();
		mapRefuseInfoImpl.encodeAll(localasnOs);

		UserInformation userInformation = TcapFactory.createUserInformation();

		userInformation.setOid(true);
		userInformation.setOidValue(MAPDialogueAS.MAP_DialogueAS.getOID());

		userInformation.setAsn(true);
		userInformation.setEncodeType(localasnOs.toByteArray());

		tcUserAbort.setApplicationContextName(tcapDialog.getApplicationContextName());
		tcUserAbort.setDialogServiceUserType(DialogServiceUserType.NoReasonGive);
		tcUserAbort.setUserInformation(userInformation);
		if (returnMessageOnError)
			tcUserAbort.setReturnMessageOnError(true);

		try {
			tcapDialog.send(tcUserAbort);
		} catch (TCAPSendException e) {
			throw new MAPException(e.getMessage(), e);
		}
	}

	/**
	 * Issue TC-U-ABORT with the ABRT apdu with MAPUserAbortInfo userInformation
	 * 
	 * @param tcapDialog
	 * @param mapUserAbortChoice
	 * @param mapExtensionContainer
	 * @throws MAPException
	 */
	protected void fireTCAbortUser(Dialog tcapDialog, MAPUserAbortChoice mapUserAbortChoice, MAPExtensionContainer mapExtensionContainer,
			boolean returnMessageOnError) throws MAPException {

		if (tcapDialog.getApplicationContextName() == null) // MAP V1
			this.fireTCAbortV1(tcapDialog, returnMessageOnError);

		TCUserAbortRequest tcUserAbort = this.getTCAPProvider().getDialogPrimitiveFactory().createUAbort(tcapDialog);

		MAPUserAbortInfoImpl mapUserAbortInfoImpl = new MAPUserAbortInfoImpl();
		mapUserAbortInfoImpl.setMAPUserAbortChoice(mapUserAbortChoice);
		mapUserAbortInfoImpl.setExtensionContainer(mapExtensionContainer);

		AsnOutputStream localasnOs = new AsnOutputStream();
		mapUserAbortInfoImpl.encodeAll(localasnOs);

		UserInformation userInformation = TcapFactory.createUserInformation();

		userInformation.setOid(true);
		userInformation.setOidValue(MAPDialogueAS.MAP_DialogueAS.getOID());

		userInformation.setAsn(true);
		userInformation.setEncodeType(localasnOs.toByteArray());
		if (returnMessageOnError)
			tcUserAbort.setReturnMessageOnError(true);

		tcUserAbort.setUserInformation(userInformation);

		try {
			tcapDialog.send(tcUserAbort);
		} catch (TCAPSendException e) {
			throw new MAPException(e.getMessage(), e);
		}

	}

	/**
	 * Issue TC-U-ABORT with the ABRT apdu with MAPProviderAbortInfo
	 * userInformation
	 * 
	 * @param tcapDialog
	 * @param mapProviderAbortReason
	 * @param mapExtensionContainer
	 * @throws MAPException
	 */
	protected void fireTCAbortProvider(Dialog tcapDialog, MAPProviderAbortReason mapProviderAbortReason, MAPExtensionContainer mapExtensionContainer,
			boolean returnMessageOnError) throws MAPException {
		
		if (tcapDialog.getApplicationContextName() == null) // MAP V1
			this.fireTCAbortV1(tcapDialog, returnMessageOnError);

		TCUserAbortRequest tcUserAbort = this.getTCAPProvider().getDialogPrimitiveFactory().createUAbort(tcapDialog);

		MAPProviderAbortInfoImpl mapProviderAbortInfo = new MAPProviderAbortInfoImpl();
		mapProviderAbortInfo.setMAPProviderAbortReason(mapProviderAbortReason);
		mapProviderAbortInfo.setExtensionContainer(mapExtensionContainer);

		AsnOutputStream localasnOs = new AsnOutputStream();
		mapProviderAbortInfo.encodeAll(localasnOs);

		UserInformation userInformation = TcapFactory.createUserInformation();

		userInformation.setOid(true);
		userInformation.setOidValue(MAPDialogueAS.MAP_DialogueAS.getOID());

		userInformation.setAsn(true);
		userInformation.setEncodeType(localasnOs.toByteArray());
		if (returnMessageOnError)
			tcUserAbort.setReturnMessageOnError(true);

		tcUserAbort.setUserInformation(userInformation);

		try {
			tcapDialog.send(tcUserAbort);
		} catch (TCAPSendException e) {
			throw new MAPException(e.getMessage(), e);
		}
	}

	/**
	 * Issue TC-U-ABORT without any apdu - for MAP V1
	 * 
	 * @param tcapDialog
	 * @param mapProviderAbortReason
	 * @param mapExtensionContainer
	 * @throws MAPException
	 */
	protected void fireTCAbortV1(Dialog tcapDialog, boolean returnMessageOnError) throws MAPException {

		TCUserAbortRequest tcUserAbort = this.getTCAPProvider().getDialogPrimitiveFactory().createUAbort(tcapDialog);
		if (returnMessageOnError)
			tcUserAbort.setReturnMessageOnError(true);

		try {
			tcapDialog.send(tcUserAbort);
		} catch (TCAPSendException e) {
			throw new MAPException(e.getMessage(), e);
		}
	}

}
