using CAAPS.Enums;
using CAAPS.Swift.MT564;
using CAAPS.SwiftDbHandler;
using Caaps_Models.Display;
using Caaps_Models.Notifications;
using CaapsWebServer.AccessLayer.Implementation;
using CaapsWebServer.AccessLayer.Interfaces;
using CaapsWebServer.Config;
using CaapsWebServer.DataLayer.DataModel;
using CaapsWebServer.DataLayer.Interfaces;
using CaapsWebServer.Enums;
using CaapsWebServer.Models.Generic;
using CaapsWebServer.Models.Response;
using CaapsWebServer.Solace;
using LogUtility.Logger;
using Microsoft.Extensions.Options;
using MoreLinq.Extensions;
using System;
using System.Collections.Generic;
using System.Dynamic;
using Caaps_Models.Solace;
using System.Linq;
using System.Text;
using Newtonsoft.Json;
using System.Text.Json;
using System.Globalization;
using Microsoft.EntityFrameworkCore;
using CaapsWebServer.Utilities;
using CAAPS;
using Microsoft.Extensions.Configuration;
using CAAPS.Common.Security;
using System.Text.RegularExpressions;
//using CAAPS.SwiftDb.Common.Security;
using System.Collections.ObjectModel;
using CaapsWebServer.Models.Request;
using Newtonsoft.Json.Linq;
using LinqToDB.Common;


namespace CaapsWebServer.DataLayer.Implementation
{
    public class EventDBHandler : IEventDBHandler
    {
        private readonly SwiftDbContextWeb swiftContext = null;
        private readonly CAAPSContext context = null;
        private readonly IEnumHelper enumHelper = null;
        private readonly LinqToDbHelper linqToDbHelper = null;

        private readonly CaapsLinqToDB.DBModels.LinqToDBSwiftContext linqToDBContext = null;

        private readonly DisplayServiceHelper displayDataHelper = null;

        private readonly DisplayToMt564 displayToMt = null;
        private readonly IAdvanceSearchService advanceSearchService = null;
        private readonly SolaceMessageListener solaceMessageListener = null;
        private readonly SolaceSettings solaceSettings;
        private readonly IAuditTrailOperationalDBHandler auditTrailDBHandler = null;
        private readonly AppNotificationSender appNotificationSender = null;
        private readonly IConfiguration configuration;

        public EventDBHandler(IConfiguration _configuration, SwiftDbContextWeb swiftContext, CAAPSContext context, IEnumHelper enumHelper, DisplayServiceHelper displayDataHelper, IAdvanceSearchService advanceSearchService, DisplayToMt564 displayToMt, SolaceMessageListener solaceMessageListener, IOptionsMonitor<SolaceSettings> solaceSettings, IAuditTrailOperationalDBHandler auditTrailDBHandler, LinqToDbHelper linqToDbHelper, CaapsLinqToDB.DBModels.LinqToDBSwiftContext linqToDBContext, AppNotificationSender appNotificationSender)
        {
            this.swiftContext = swiftContext;
            this.context = context;
            this.enumHelper = enumHelper;
            this.displayDataHelper = displayDataHelper;
            this.advanceSearchService = advanceSearchService;
            this.displayToMt = displayToMt;
            this.solaceMessageListener = solaceMessageListener;
            this.solaceSettings = solaceSettings.CurrentValue;
            this.auditTrailDBHandler = auditTrailDBHandler;
            this.linqToDbHelper = linqToDbHelper;
            this.linqToDBContext = linqToDBContext;
            this.appNotificationSender = appNotificationSender;
            this.configuration = _configuration;
        }

        public ResponseParent EventApprovalRejection(int goldenRecordId, int Status, string Role, ApproveReject detail)
        {
            ResponseParent res = new ResponseParent();
            try
            {
                Logger.Log("DisplayObject = " + JsonConvert.SerializeObject(detail.displayData), LogType.Debug);
                Logger.Log("ApproveReject detail: IsSuppressNotificationEnabled = " + detail.IsSuppressNotificationEnabled + ", Action: " + (detail.IsSuppressNotificationEnabled ? "Suppressing notifications" : "Notifications enabled"), LogType.Debug);
                if (Role != UserRoleConsts.SUPERVISOR && Role != UserRoleConsts.SYSTEM_ADMIN)
                {
                    res.IsSuccess = false;
                    res.Message = this.enumHelper.GetEnumDescription(ResponseCodeMessage.NoPermission);
                    res.MessageId = (int)ResponseCodeMessage.NoPermission;
                }
                else
                {
                    GoldenRecordInternalChatDetail newComment = new GoldenRecordInternalChatDetail();
                    newComment.ChatText = detail.comment;
                    newComment.GoldenRecordId = goldenRecordId;
                    newComment.IsActive = 1;
                    newComment.OriginatedFromAction = detail.OriginatedFromAction;
                    newComment.EntryBy = detail.EntryBy;
                    newComment.EntryDtTime = DateTime.Now;
                    newComment.EntryDtTimeUtc = DateTime.UtcNow;
                    this.context.GoldenRecordInternalChatDetails.Add(newComment);
                    this.context.SaveChanges();

                    List<CaEventConflict> allConflicts = this.context.CaEventConflicts.Where(x => x.GoldenRecordId == goldenRecordId).ToList();
                    GoldenRecordMst g = this.context.GoldenRecordMsts.Where(x => x.GoldenRecordId == goldenRecordId).FirstOrDefault();
                    CaapsLinqToDB.DBModels.MT564Message message = this.linqToDbHelper.GetEventDetailsByGolderRecordId((long)goldenRecordId);

                    GoldenRecordMst grm = this.context.GoldenRecordMsts.Where(x => x.GoldenRecordId == goldenRecordId).FirstOrDefault();
                    string[] GPDFields = new string[] { };

                    GPDFields = (from cg in this.context.CaEventConfigs
                                 join cem in this.context.CaEventTypeMsts on cg.CaEventTypeId equals cem.CaEventTypeId
                                 join ce in this.context.CaEventMsts on cg.CaEventMstId equals ce.CaEventMstId
                                 where ce.CaEventCode == grm.EventName && cem.CaEventType == grm.EventMvc
                                 select cg.GpdDateType).FirstOrDefault().Split(',');

                    if (GPDFields.Length == 1)
                    {
                        grm.GpdDateType = GPDFields[0];
                        this.context.GoldenRecordMsts.Update(grm);
                        this.context.SaveChanges();
                    }
                    else if (GPDFields.Length > 0)
                    {
                        string matchDateData = System.IO.File.ReadAllText("./MatchDates.json", Encoding.UTF8);
                        var dateField = System.Text.Json.JsonSerializer.Deserialize<List<MatchDateModel>>(matchDateData);
                        IDictionary<string, DateTime?> objGPDFieldsData = new Dictionary<string, DateTime?>();
                        for (int i = 0; i < GPDFields.Length; i++)
                        {
                            objGPDFieldsData.Add(
                                GPDFields[i],
                                detail.displayData.GetType().GetProperty(dateField.Where(x => x.Qualifier == GPDFields[i]).FirstOrDefault().MatchDate).GetValue(detail.displayData) == null ? null :
                                Convert.ToDateTime(detail.displayData.GetType().GetProperty(dateField.Where(x => x.Qualifier == GPDFields[i]).FirstOrDefault().MatchDate).GetValue(detail.displayData).ToString())
                                );
                        }

                        if (!objGPDFieldsData.Any(x => x.Value != null))
                        {
                            grm.GpdDateType = "NONE";
                        }
                        else if (objGPDFieldsData.Where(x => x.Value != null).Count() == 1)
                        {
                            grm.GpdDateType = objGPDFieldsData.Where(x => x.Value != null).FirstOrDefault().Key;
                        }
                        else if (objGPDFieldsData.Where(x => x.Value != null).Count() > 1)
                        {
                            grm.GpdDateType = objGPDFieldsData.Where(x => x.Value != null).OrderBy(x => x.Value).FirstOrDefault().Key;
                        }
                        this.context.GoldenRecordMsts.Update(grm);
                        this.context.SaveChanges();
                    }


                    if (Status == 1) //Approved
                    {
                        List<CaEventConflict> conflicts = allConflicts.Where(x => x.ReviewStatus == (int)EventReviewStatus.InReview).ToList();

                        if (g.GoldenRecordStatus == (string)EventWorkFlowStatus.PendingCancel)
                        {
                            if (g != null)
                            {
                                message.MessageData.GeneralInformation.FunctionOfMessage.Function = "CANC";
                                message.MessageData.GeneralInformation.StatusCode = EventProcessingStatus.COMP;
                                AdditionalInfoFlags flagDetails = this.linqToDbHelper.getAdditionFlags(message);
                                this.linqToDBContext.Update(message);
                                this.linqToDBContext.SaveChanges();
                                Logger.Log($"EventApprovalRejection EventWorkFlowStatus.Cancelled", LogType.Info);
                                g.GoldenRecordStatus = EventWorkFlowStatus.Cancelled;
                                g.ReviewByDate = null;
                                g.IsAdditionalTextUpdated = flagDetails.IsAdditionTextUpdated;
                                g.IsInformationConditionsUpdated = flagDetails.IsInformationConditionsUpdated;
                                g.IsInformationToComplyUpdated = flagDetails.IsInformationToComplyUpdated;
                                g.IsNarrativeVersionUpdated = flagDetails.IsNarrativeVersionUpdated;
                                g.IsTaxationConditionsUpdated = flagDetails.IsTaxationConditionUpdated;
                                g.EarlyResponseDeadlineDate = flagDetails.EarlyResponseDeadline;
                                g.ProtectDate = flagDetails.ProtectDate;
                                g.ResponseDeadlineDate = flagDetails.ResponseDeadline;
                                g.MarketDeadlineDate = flagDetails.MarketDeadline;
                                g.IsoChangeType = flagDetails.ISOChangeType;
                                g.IsoEffectiveDate = flagDetails.ISOEffectiveDate;
                                g.IsoExDividendOrDistributionDate = flagDetails.ISOExDividendDate;
                                g.IsoExpiryDate = flagDetails.ISOExpiryDate;
                                g.IsoOfferor = flagDetails.ISOOfferor;
                                g.IsoRecordDate = flagDetails.ISORecordDate;
                                g.IsTouched = true;
                                g.IsAutoStp = false;
                                g.EntryDtTime = DateTime.Now;
                                g.EntryDtTimeUtc = DateTime.UtcNow;
                                g.EntryBy = detail.EntryBy;
                                this.context.SaveChanges();

                                this.SetResponseDeadlineDates(goldenRecordId, message, detail.EntryBy);

                                res.IsSuccess = true;
                                res.Message = this.enumHelper.GetEnumDescription(ResponseCodeMessage.Successful);
                                res.MessageId = (int)ResponseCodeMessage.Successful;
                                /*
                                    As per CR#110 we dont need to send position notifications to client when Suppress Notification Enable check is false.
                                */
                                if (!detail.IsSuppressNotificationEnabled)
                                {
                                    ResponseParent solaceMessage = this.SendSolaceMessageLinqDB(solaceSettings.PositionCheckQueueName, message, g);
                                    Logger.Log("Solace Message sending = " + solaceMessage.IsSuccess, LogType.Debug);
                                }
                            }
                            else
                            {
                                res.IsSuccess = false;
                                res.Message = this.enumHelper.GetEnumDescription(ResponseCodeMessage.NotFound);
                                res.MessageId = (int)ResponseCodeMessage.NotFound;
                            }
                        }
                        else if (g.GoldenRecordStatus == (string)EventWorkFlowStatus.PendingWithdraw)
                        {
                            if (g != null)
                            {
                                message.MessageData.GeneralInformation.FunctionOfMessage.Function = "WITH";
                                message.MessageData.GeneralInformation.StatusCode = EventProcessingStatus.COMP;
                                AdditionalInfoFlags flagDetails = this.linqToDbHelper.getAdditionFlags(message);
                                this.linqToDBContext.Update(message);
                                this.linqToDBContext.SaveChanges();
                                Logger.Log($"EventApprovalRejection EventWorkFlowStatus.Withdrawn", LogType.Info);
                                g.GoldenRecordStatus = EventWorkFlowStatus.Withdrawn;
                                g.IsAdditionalTextUpdated = flagDetails.IsAdditionTextUpdated;
                                g.IsInformationConditionsUpdated = flagDetails.IsInformationConditionsUpdated;
                                g.IsInformationToComplyUpdated = flagDetails.IsInformationToComplyUpdated;
                                g.IsNarrativeVersionUpdated = flagDetails.IsNarrativeVersionUpdated;
                                g.IsTaxationConditionsUpdated = flagDetails.IsTaxationConditionUpdated;
                                g.IsTouched = true;
                                g.EarlyResponseDeadlineDate = flagDetails.EarlyResponseDeadline;
                                g.ProtectDate = flagDetails.ProtectDate;
                                g.ResponseDeadlineDate = flagDetails.ResponseDeadline;
                                g.MarketDeadlineDate = flagDetails.MarketDeadline;
                                g.IsoChangeType = flagDetails.ISOChangeType;
                                g.IsoEffectiveDate = flagDetails.ISOEffectiveDate;
                                g.IsoExDividendOrDistributionDate = flagDetails.ISOExDividendDate;
                                g.IsoExpiryDate = flagDetails.ISOExpiryDate;
                                g.IsoOfferor = flagDetails.ISOOfferor;
                                g.IsoRecordDate = flagDetails.ISORecordDate;
                                g.ReviewByDate = null;
                                g.IsAutoStp = false;
                                g.EntryDtTime = DateTime.Now;
                                g.EntryDtTimeUtc = DateTime.UtcNow;
                                g.EntryBy = detail.EntryBy;
                                this.context.SaveChanges();
                                this.SetResponseDeadlineDates(goldenRecordId, message, detail.EntryBy);
                                res.IsSuccess = true;
                                res.Message = this.enumHelper.GetEnumDescription(ResponseCodeMessage.Successful);
                                res.MessageId = (int)ResponseCodeMessage.Successful;
                                /*
                                    As per CR#110 we dont need to send position notifications to client when Suppress Notification Enable check is false.
                                */
                                if (!detail.IsSuppressNotificationEnabled)
                                {
                                    ResponseParent solaceMessage = this.SendSolaceMessageLinqDB(solaceSettings.PositionCheckQueueName, message, g);
                                    Logger.Log("Solace Message sending = " + solaceMessage.IsSuccess, LogType.Debug);
                                }
                            }
                            else
                            {
                                res.IsSuccess = false;
                                res.Message = this.enumHelper.GetEnumDescription(ResponseCodeMessage.NotFound);
                                res.MessageId = (int)ResponseCodeMessage.NotFound;
                            }
                        }
                        else if (g.GoldenRecordStatus == (string)EventWorkFlowStatus.PendingDelete)
                        {
                            DeleteEventModel model = new DeleteEventModel();
                            model.MessageFrom = Notification.Component.WEB_SERVER;
                            model.MessageAction = "DELETE_EVENT";
                            model.GoldenRecordId = goldenRecordId;
                            model.IsSuppressNotificationEnabled = detail.IsSuppressNotificationEnabled;
                            if (solaceSettings.SendToTopic)
                            {
                                this.solaceMessageListener.SendToTopic(solaceSettings.MatchingTopicName, JsonConvert.SerializeObject(model));
                            }
                            else
                            {
                                this.solaceMessageListener.SendToQueue(solaceSettings.MatchingQueueName, JsonConvert.SerializeObject(model));
                            }
                            res.IsSuccess = true;
                            res.Message = this.enumHelper.GetEnumDescription(ResponseCodeMessage.Successful);
                            res.MessageId = (int)ResponseCodeMessage.Successful;
                        }
                        else
                        {

                            conflicts.ForEach(conflict =>
                            {
                                conflict.FieldStatus = (byte)EventFieldStatus.Complete;
                                conflict.ReviewStatus = (byte)EventReviewStatus.UserResolved;
                                conflict.EntryBy = detail.EntryBy;
                                conflict.EntryDtTime = DateTime.Now;
                                conflict.EntryDtTimeUtc = DateTime.UtcNow;

                                if (conflict.FieldName.Contains("securityid"))
                                {
                                    GoldenRecordSecurityDetail existingObject = this.context.GoldenRecordSecurityDetails.Where(x => x.GoldenRecordId == conflict.GoldenRecordId
                                    && x.OptionNumber == conflict.OptionNumber
                                    && x.MovementId == conflict.MovementId).FirstOrDefault();
                                    var secIdType = detail.displayData.Options.Where(x =>
                                                    x.CaOptOptionNumber == conflict.OptionNumber)
                                                    .FirstOrDefault().securityMovement.Where(y =>
                                                        y.SMSecurityNumber == conflict.MovementId)
                                                    .FirstOrDefault().SMNewSecurityIDType;
                                    var secId = detail.displayData.Options.Where(x =>
                                                x.CaOptOptionNumber == conflict.OptionNumber)
                                                .FirstOrDefault().securityMovement.Where(y =>
                                                    y.SMSecurityNumber == conflict.MovementId)
                                                .FirstOrDefault().SMNewSecurityID;

                                    if (existingObject == null)
                                    {
                                        existingObject = new GoldenRecordSecurityDetail();
                                        existingObject.GoldenRecordId = conflict.GoldenRecordId;
                                        existingObject.OptionNumber = conflict.OptionNumber;
                                        existingObject.MovementId = conflict.MovementId;
                                        existingObject.CUSIP = secIdType == "CUSP" ? secId : "";
                                        existingObject.ISIN = secIdType == "ISIN" ? secId : "";
                                        existingObject.Sedol = secIdType == "Sedol" ? secId : "";
                                        existingObject.BloombergId = "";
                                        existingObject.ShortName = "";
                                        existingObject.LongName = "";
                                        existingObject.IsActive = 1;
                                        existingObject.EntryBy = detail.EntryBy;
                                        existingObject.EntryDtTime = DateTime.Now;
                                        existingObject.EntryDtTimeUtc = DateTime.UtcNow;
                                        this.context.GoldenRecordSecurityDetails.Add(existingObject);
                                    }
                                    else
                                    {
                                        if ((secIdType == "CUSP" && secId != existingObject.CUSIP)
                                        || (secIdType == "ISIN" && secId != existingObject.ISIN)
                                        || (secIdType == "Sedol" && secId != existingObject.Sedol)
                                        )
                                        {
                                            existingObject.CUSIP = secIdType == "CUSP" ? secId : "";
                                            existingObject.ISIN = secIdType == "ISIN" ? secId : "";
                                            existingObject.Sedol = secIdType == "Sedol" ? secId : "";
                                            existingObject.BloombergId = "";
                                            existingObject.ShortName = "";
                                            existingObject.LongName = "";
                                            this.context.GoldenRecordSecurityDetails.Update(existingObject);
                                        }
                                    }
                                    this.context.SaveChanges();
                                }

                                this.context.SaveChanges();
                            });

                            //getting null pointer exception from TransformDisplayToMt564 issue fix
                            CaapsLinqToDB.DBModels.MT564Message cloneMessage = JsonConvert.DeserializeObject<CaapsLinqToDB.DBModels.MT564Message>(JsonConvert.SerializeObject(message, Formatting.None, new JsonSerializerSettings { ReferenceLoopHandling = ReferenceLoopHandling.Ignore }));
                            CaapsLinqToDB.DBModels.MT564Message UpdatedMessage = this.linqToDbHelper.TransformDisplayToMt564(message, detail.displayData);
                            UpdatedMessage.MessageData.GeneralInformation.FunctionOfMessage.Function = "REPL";
                            UpdatedMessage.MessageData.GeneralInformation.StatusCode = EventProcessingStatus.COMP;

                            Boolean GPDUpdateOrNot = this.setGPDDate(cloneMessage, UpdatedMessage, detail.displayData.EventName, detail.displayData.EventMvc);
                            AdditionalInfoFlags flagDetails = this.linqToDbHelper.getAdditionFlags(UpdatedMessage);
                            Logger.Log("Updated MT564 Message = " + JsonConvert.SerializeObject(UpdatedMessage, Formatting.None, new JsonSerializerSettings { ReferenceLoopHandling = ReferenceLoopHandling.Ignore }), LogType.Debug);

                            this.linqToDBContext.Update(UpdatedMessage);
                            this.linqToDBContext.SaveChanges();

                            if (g != null)
                            {
                                Logger.Log($"EventApprovalRejection EventWorkFlowStatus.Complete", LogType.Info);
                                g.GoldenRecordStatus = EventWorkFlowStatus.Complete;
                                g.IsAdditionalTextUpdated = flagDetails.IsAdditionTextUpdated;
                                g.IsInformationConditionsUpdated = flagDetails.IsInformationConditionsUpdated;
                                g.IsInformationToComplyUpdated = flagDetails.IsInformationToComplyUpdated;
                                g.IsNarrativeVersionUpdated = flagDetails.IsNarrativeVersionUpdated;
                                g.IsTaxationConditionsUpdated = flagDetails.IsTaxationConditionUpdated;
                                g.IsTouched = true;
                                g.EarlyResponseDeadlineDate = flagDetails.EarlyResponseDeadline;
                                g.ProtectDate = flagDetails.ProtectDate;
                                g.ResponseDeadlineDate = flagDetails.ResponseDeadline;
                                g.MarketDeadlineDate = flagDetails.MarketDeadline;
                                g.IsoChangeType = flagDetails.ISOChangeType;
                                g.IsoEffectiveDate = flagDetails.ISOEffectiveDate;
                                g.IsoExDividendOrDistributionDate = flagDetails.ISOExDividendDate;
                                g.IsoExpiryDate = flagDetails.ISOExpiryDate;
                                g.IsoOfferor = flagDetails.ISOOfferor;
                                g.IsoRecordDate = flagDetails.ISORecordDate;
                                g.ReviewByDate = null;
                                g.IsAutoStp = false;
                                g.EntryDtTime = DateTime.Now;
                                g.EntryDtTimeUtc = DateTime.UtcNow;
                                g.EntryBy = detail.EntryBy;
                                if (GPDUpdateOrNot == true)
                                {
                                    g.PositionFixDate = null;
                                    g.IsPositionCaptured = false;
                                    this.context.Database.ExecuteSqlRaw($"Exec Proc_Web_Submit_GoldenRecord_Position_Flag_Update_All_Related_Securities {goldenRecordId}");
                                }
                                this.context.SaveChanges();
                                this.SetResponseDeadlineDates(goldenRecordId, message, detail.EntryBy);
                                res.IsSuccess = true;
                                res.Message = this.enumHelper.GetEnumDescription(ResponseCodeMessage.Successful);
                                res.MessageId = (int)ResponseCodeMessage.Successful;
                                /*
                                    As per CR#110 we dont need to send position notifications to client when Suppress Notification Enable check is false.
                                */
                                if (!detail.IsSuppressNotificationEnabled)
                                {
                                    ResponseParent solaceMessage = this.SendSolaceMessageLinqDB(solaceSettings.PositionCheckQueueName, UpdatedMessage, g);
                                    Logger.Log("Solace Message sending = " + solaceMessage.IsSuccess, LogType.Debug);
                                }
                            }
                            else
                            {
                                res.IsSuccess = false;
                                res.Message = this.enumHelper.GetEnumDescription(ResponseCodeMessage.NotFound);
                                res.MessageId = (int)ResponseCodeMessage.NotFound;
                            }
                        }
                    }
                    else //Rejected
                    {
                        List<CaEventConflict> conflicts = allConflicts.Where(x => x.ReviewStatus == (int)EventReviewStatus.InReview).ToList();
                        conflicts.ForEach(conflict =>
                        {
                            conflict.ReviewStatus = (byte)EventReviewStatus.Open;
                            conflict.EntryDtTime = DateTime.Now;
                            conflict.EntryDtTimeUtc = DateTime.UtcNow;
                            this.context.SaveChanges();
                        });

                        if (g != null)
                        {
                            List<int> fieldStatus = allConflicts.Select(x => x.FieldStatus).ToList();
                            int finalStatus = fieldStatus.Max();
                            if (finalStatus == (int)EventFieldStatus.Incomplete)
                            {
                                Logger.Log($"EventApprovalRejection Rejected EventWorkFlowStatus.Incomplete", LogType.Info);
                                g.GoldenRecordStatus = EventWorkFlowStatus.Incomplete;
                                message.MessageData.GeneralInformation.StatusCode = EventProcessingStatus.PREU;
                            }
                            else if (finalStatus == (int)EventFieldStatus.Conflict)
                            {
                                Logger.Log($"EventApprovalRejection Rejected EventWorkFlowStatus.Conflict", LogType.Info);
                                g.GoldenRecordStatus = EventWorkFlowStatus.Conflict;
                                message.MessageData.GeneralInformation.StatusCode = EventProcessingStatus.PREU;
                            }
                            else if (finalStatus == (int)EventFieldStatus.Complete)
                            {
                                Logger.Log($"EventApprovalRejection Rejected EventWorkFlowStatus.Complete", LogType.Info);
                                g.GoldenRecordStatus = EventWorkFlowStatus.Complete;
                                message.MessageData.GeneralInformation.StatusCode = EventProcessingStatus.PREU;
                            }
                            else if (finalStatus == (int)EventFieldStatus.Updated)
                            {
                                Logger.Log($"EventApprovalRejection Rejected EventWorkFlowStatus.Updated", LogType.Info);
                                g.GoldenRecordStatus = EventWorkFlowStatus.Updated;
                                message.MessageData.GeneralInformation.StatusCode = EventProcessingStatus.PREU;
                            }
                            else
                            {
                                GoldenRecordMstAudit ga = this.context.GoldenRecordMstAudits.Where(x => x.GoldenRecordId == goldenRecordId).FirstOrDefault();
                                if (ga != null)
                                {
                                    g.GoldenRecordStatus = ga.GoldenRecordStatus;
                                    message.MessageData.GeneralInformation.StatusCode = EventProcessingStatus.PREU;
                                }
                            }
                            g.ReviewByDate = null;
                            g.IsAutoStp = false;
                            g.EntryBy = detail.EntryBy;
                            g.EntryDtTime = DateTime.Now;
                            g.EntryDtTimeUtc = DateTime.UtcNow;
                            this.context.SaveChanges();
                            this.linqToDBContext.Update(message);
                            this.linqToDBContext.SaveChanges();
                            res.IsSuccess = true;
                            res.Message = this.enumHelper.GetEnumDescription(ResponseCodeMessage.Successful);
                            res.MessageId = (int)ResponseCodeMessage.Successful;
                            /*
                                As per CR#110 we dont need to send position notifications to client in case of rejected.
                             */
                            //ResponseParent solaceMessage = this.SendSolaceMessageLinqDB(solaceSettings.PositionCheckQueueName, message, g);
                            //Logger.Log("Solace Message sending = " + solaceMessage.IsSuccess, LogType.Debug);
                        }
                        else
                        {
                            res.IsSuccess = false;
                            res.Message = this.enumHelper.GetEnumDescription(ResponseCodeMessage.NotFound);
                            res.MessageId = (int)ResponseCodeMessage.NotFound;
                        }
                    }



                }
                this.SaveInActiveDate(goldenRecordId);
                sendEventNotification(
                     $"Event approval perform",
                     "EVENT_APPROVAL",
                      new
                      {
                          GoldenId = goldenRecordId
                      });

            }
            catch (Exception ex)
            {
                Console.WriteLine(ex);
                res.IsSuccess = false;
                res.Message = this.enumHelper.GetEnumDescription(ResponseCodeMessage.InternalServerError);
                res.MessageId = (int)ResponseCodeMessage.InternalServerError;

                dynamic logs = new ExpandoObject();
                logs.Data = JsonConvert.SerializeObject(detail);
                logs.StackTrace = ex.StackTrace;
                logs.goldenId = goldenRecordId;
                logs.status = Status;
                logs.role = Role;
                Logger.LogException(ex, LogType.Error, logs);
                this.appNotificationSender.sendDbFailureNotification(ex.Message, logs);
            }

            return res;
        }

        public WorkQueueResponse GetWorkQueueByUserId(int workQueueId, int userId)
        {
            WorkQueueResponse workQueueResponse = new WorkQueueResponse();
            try
            {
                if (userId > 0)
                {
                    workQueueResponse = (from wqm in this.context.WorkQueueMsts
                                         join wqum in this.context.WorkQueueUserMappings on wqm.WorkQueueId equals wqum.WorkQueueId
                                         where ((wqm.WorkQueueId == workQueueId) && (wqum.UserId == userId))
                                         select new WorkQueueResponse()
                                         {
                                             WorkQueueId = (int)wqm.WorkQueueId,
                                             WorkQueueName = wqm.WorkQueueName,
                                             WorkQueueObject = wqm.WorkQueueObject,
                                             EntryBy = wqm.EntryBy,
                                             EntryDtTime = wqm.EntryDtTime,
                                             EntryDtTimeUtc = wqm.EntryDtTimeUtc,
                                             IsActive = wqm.IsActive
                                         }).FirstOrDefault();
                }
                else
                {
                    workQueueResponse = (from wqm in this.context.WorkQueueMsts
                                         where ((wqm.WorkQueueId == workQueueId))
                                         select new WorkQueueResponse()
                                         {
                                             WorkQueueId = (int)wqm.WorkQueueId,
                                             WorkQueueName = wqm.WorkQueueName,
                                             WorkQueueObject = wqm.WorkQueueObject,
                                             EntryBy = wqm.EntryBy,
                                             EntryDtTime = wqm.EntryDtTime,
                                             EntryDtTimeUtc = wqm.EntryDtTimeUtc,
                                             IsActive = wqm.IsActive
                                         }).FirstOrDefault();
                }


                if (workQueueResponse != null)
                {
                    workQueueResponse.IsSuccess = true;
                    workQueueResponse.Message = this.enumHelper.GetEnumDescription(ResponseCodeMessage.Successful);
                    workQueueResponse.MessageId = (int)ResponseCodeMessage.Successful;
                }
                else
                {
                    workQueueResponse.IsSuccess = true;
                    workQueueResponse.Message = this.enumHelper.GetEnumDescription(ResponseCodeMessage.NoRecord);
                    workQueueResponse.MessageId = (int)ResponseCodeMessage.NoRecord;
                }
            }
            catch (Exception ex)
            {
                workQueueResponse.IsSuccess = false;
                workQueueResponse.Message = this.enumHelper.GetEnumDescription(ResponseCodeMessage.InternalServerError);
                workQueueResponse.MessageId = (int)ResponseCodeMessage.InternalServerError;
                dynamic logs = new ExpandoObject();
                logs.StackTrace = ex.StackTrace;
                logs.workQueueId = workQueueId;
                logs.UserId = userId;
                Logger.LogException(ex, LogType.Error, logs);
                this.appNotificationSender.sendDbFailureNotification(ex.Message, logs);
            }
            return workQueueResponse;
        }


        public List<WorkQueueResponse> GetWorkQueuesByUserId(int userId)
        {
            List<WorkQueueResponse> workQueueResponses = new List<WorkQueueResponse>();
            try
            {
                if (userId > 0)
                {
                    workQueueResponses = (from wqm in this.context.WorkQueueMsts
                                          join wqum in this.context.WorkQueueUserMappings on wqm.WorkQueueId equals wqum.WorkQueueId
                                          where (wqum.UserId == userId)
                                          where (wqm.IsActive == 1)
                                          select new WorkQueueResponse()
                                          {
                                              WorkQueueId = (int)wqm.WorkQueueId,
                                              WorkQueueName = wqm.WorkQueueName,
                                              WorkQueueObject = wqm.WorkQueueObject,
                                              EntryBy = wqm.EntryBy,
                                              EntryDtTime = wqm.EntryDtTime,
                                              EntryDtTimeUtc = wqm.EntryDtTimeUtc,
                                              IsActive = wqm.IsActive
                                          }).ToList();

                }
                else
                {
                    workQueueResponses = (from wqm in this.context.WorkQueueMsts
                                          where (wqm.IsActive == 1)
                                          select new WorkQueueResponse()
                                          {
                                              WorkQueueId = (int)wqm.WorkQueueId,
                                              WorkQueueName = wqm.WorkQueueName,
                                              WorkQueueObject = wqm.WorkQueueObject,
                                              EntryBy = wqm.EntryBy,
                                              EntryDtTime = wqm.EntryDtTime,
                                              EntryDtTimeUtc = wqm.EntryDtTimeUtc,
                                              IsActive = wqm.IsActive
                                          }).ToList();
                }

            }
            catch (Exception ex)
            {
                Logger.LogException(ex, LogType.Error, ex.StackTrace);
                this.appNotificationSender.sendDbFailureNotification(ex.Message, ex.StackTrace);
            }
            return workQueueResponses;
        }
        public List<WorkQueueResponse> GetWorkQueuesByUserIdAndWqName(int userId, string name)
        {
            List<WorkQueueResponse> workQueueResponses = new List<WorkQueueResponse>();
            try
            {
                if (userId > 0)
                {
                    workQueueResponses = (from wqm in this.context.WorkQueueMsts
                                          join wqum in this.context.WorkQueueUserMappings on wqm.WorkQueueId equals wqum.WorkQueueId
                                          where (wqum.UserId == userId)
                                          where (wqm.IsActive == 1)
                                          where (wqm.WorkQueueName.ToLower() == name.ToLower())
                                          select new WorkQueueResponse()
                                          {
                                              WorkQueueId = (int)wqm.WorkQueueId,
                                              WorkQueueName = wqm.WorkQueueName,
                                              WorkQueueObject = wqm.WorkQueueObject,
                                              EntryBy = wqm.EntryBy,
                                              EntryDtTime = wqm.EntryDtTime,
                                              EntryDtTimeUtc = wqm.EntryDtTimeUtc,
                                              IsActive = wqm.IsActive
                                          }).ToList();

                }
                else
                {
                    workQueueResponses = (from wqm in this.context.WorkQueueMsts
                                          where (wqm.IsActive == 1)
                                          where (wqm.WorkQueueName.ToLower() == name.ToLower())
                                          select new WorkQueueResponse()
                                          {
                                              WorkQueueId = (int)wqm.WorkQueueId,
                                              WorkQueueName = wqm.WorkQueueName,
                                              WorkQueueObject = wqm.WorkQueueObject,
                                              EntryBy = wqm.EntryBy,
                                              EntryDtTime = wqm.EntryDtTime,
                                              EntryDtTimeUtc = wqm.EntryDtTimeUtc,
                                              IsActive = wqm.IsActive
                                          }).ToList();
                }

            }
            catch (Exception ex)
            {
                Logger.LogException(ex, LogType.Error, ex.StackTrace);
                this.appNotificationSender.sendDbFailureNotification(ex.Message, ex.StackTrace);
            }
            return workQueueResponses;
        }

        public List<CaEventConflict> GetEventConflictDetailsByGoldenId(int goldenId, int userId)
        {
            List<CaEventConflict> conflictDetails = new List<CaEventConflict>();
            GoldenRecordMst g = new GoldenRecordMst();
            try
            {
                conflictDetails = this.context.CaEventConflicts.Where(c => c.GoldenRecordId == goldenId).ToList();
                g = this.context.GoldenRecordMsts.Where(x => x.GoldenRecordId == goldenId).FirstOrDefault();
                if (!conflictDetails.Any(x => x.FieldName.Contains("letterofguaranteeddelivery")) && IsConditional(g))
                {
                    DisplayData displayData = this.linqToDbHelper.GetDisplayDataByGoldenRecordId((int)goldenId);
                    if (displayData.LetterofGuaranteedDelivery == null)
                    {
                        AllSourceResponse sources = GetAllSourceByGoldenId(goldenId);
                        CaEventConflict caEventConflict = new CaEventConflict();
                        caEventConflict.GoldenRecordId = displayData.CaapsId;
                        caEventConflict.FieldName = "eventdetail.letterofguaranteeddelivery.flag";
                        caEventConflict.OptionNumber = "";
                        caEventConflict.MovementId = "";
                        caEventConflict.ExistingSource = sources.Sources?.FirstOrDefault().EventSourceName;
                        caEventConflict.ClientValue = "";
                        caEventConflict.NewEventId = 0;
                        caEventConflict.NewEventSource = "";
                        caEventConflict.NewEventValue = "";
                        caEventConflict.NewEventOptionNumber = "";
                        caEventConflict.NewEventMovementId = "";
                        caEventConflict.IsActive = 1;
                        caEventConflict.EntryBy = userId;
                        caEventConflict.EntryDtTime = DateTime.Now;
                        caEventConflict.EntryDtTimeUtc = DateTime.UtcNow;
                        caEventConflict.FieldStatus = 2;
                        caEventConflict.ReviewStatus = 1;
                        this.context.CaEventConflicts.Add(caEventConflict);
                        displayData.Options.ForEach(option =>
                        {
                            CaEventConflict CaOptCoverExpirationDate = this.context.CaEventConflicts.Where(x => x.GoldenRecordId == goldenId && x.FieldName == "optiondetail.coverexpirationdate.cadatetime" && x.OptionNumber == option.CaOptOptionNumber).FirstOrDefault();
                            CaEventConflict CaOptDepositoryCoverExpirationDate = this.context.CaEventConflicts.Where(x => x.GoldenRecordId == goldenId && x.FieldName == "optiondetail.depositorycoverexpirationdate.cadatetime" && x.OptionNumber == option.CaOptOptionNumber).FirstOrDefault();
                            CaOptCoverExpirationDate.FieldStatus = 0;
                            CaOptCoverExpirationDate.ReviewStatus = 3;
                            CaOptDepositoryCoverExpirationDate.FieldStatus = 0;
                            CaOptDepositoryCoverExpirationDate.ReviewStatus = 3;
                            this.context.CaEventConflicts.Update(CaOptCoverExpirationDate);
                            this.context.CaEventConflicts.Update(CaOptDepositoryCoverExpirationDate);
                            this.context.SaveChanges();
                        });
                        this.context.SaveChanges();
                        conflictDetails = this.context.CaEventConflicts.Where(c => c.GoldenRecordId == goldenId).ToList();
                    }

                    if (g.EventName == "OTHR" && g.EventMvc == "VOLU")
                    {
                        int totalOptions = displayData.Options.Count;
                        if (totalOptions > 0)
                        {
                            for (int i = 0; i < totalOptions; i++)
                            {
                                string currentOptionNumber = displayData.Options[i].CaOptOptionNumber;
                                if (currentOptionNumber != "999")
                                {
                                    if (!conflictDetails.Any(x => x.FieldName.Contains("optiondetail.coverexpirationdate.cadatetime") && x.OptionNumber == currentOptionNumber))
                                    {
                                        AllSourceResponse sources = GetAllSourceByGoldenId(goldenId);
                                        CaEventConflict caEventConflict = new CaEventConflict();
                                        caEventConflict.GoldenRecordId = displayData.CaapsId;
                                        caEventConflict.FieldName = "optiondetail.coverexpirationdate.cadatetime";
                                        caEventConflict.OptionNumber = "";
                                        caEventConflict.MovementId = "";
                                        caEventConflict.ExistingSource = sources.Sources?.FirstOrDefault().EventSourceName;
                                        caEventConflict.ClientValue = "";
                                        caEventConflict.NewEventId = 0;
                                        caEventConflict.NewEventSource = "";
                                        caEventConflict.NewEventValue = "";
                                        caEventConflict.NewEventOptionNumber = "";
                                        caEventConflict.NewEventMovementId = "";
                                        caEventConflict.IsActive = 1;
                                        caEventConflict.EntryBy = userId;
                                        caEventConflict.EntryDtTime = DateTime.Now;
                                        caEventConflict.EntryDtTimeUtc = DateTime.UtcNow;
                                        caEventConflict.FieldStatus = (displayData.LetterofGuaranteedDelivery == "Y" ? 2 : 0);
                                        caEventConflict.ReviewStatus = 1;
                                        this.context.CaEventConflicts.Add(caEventConflict);
                                        this.context.SaveChanges();
                                        conflictDetails = this.context.CaEventConflicts.Where(c => c.GoldenRecordId == goldenId).ToList();
                                    }
                                }
                            }
                        }
                    }
                }
                conflictDetails = this.context.CaEventConflicts.Where(c => c.GoldenRecordId == goldenId).ToList();
            }
            catch (Exception ex)
            {
                dynamic logs = new ExpandoObject();
                logs.StackTrace = ex.StackTrace;
                logs.goldenId = goldenId;
                Logger.LogException(ex, LogType.Error, logs);
                this.appNotificationSender.sendDbFailureNotification(ex.Message, logs);
            }

            return conflictDetails;
        }
    }
}