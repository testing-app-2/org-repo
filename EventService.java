package org.parade_deck.backend.services;

import com.stripe.exception.StripeException;
import com.stripe.model.AccountLink;
import com.stripe.model.Product;
import com.stripe.net.RequestOptions;
import com.stripe.param.AccountLinkCreateParams;
import org.springframework.data.domain.*;
import org.parade_deck.backend.db.postgre.models.*;
import org.parade_deck.backend.db.postgre.models.EventCategoryMapping;
import org.parade_deck.backend.dtos.*;
import org.parade_deck.backend.records.*;
import org.parade_deck.backend.db.postgre.repository.*;
import org.parade_deck.backend.db.postgre.repository.EventTicketsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.stripe.model.Account;
import com.stripe.param.AccountCreateParams;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.sql.Date;
import java.util.stream.Collectors;

@Service
public class EventService {

    @Autowired
    private EventMstRepository eventMstRepository;

    @Autowired
    private CategoryMstRepository categoryMstRepository;

    @Autowired
    private UserMstRepository userMstRepository;

    @Autowired
    private UserEventPermissionMappingRepository userEventPermissionMappingRepository;

    @Autowired
    private EventTicketsRepository eventTicketsRepository;

    @Autowired
    private EventCategoryMappingRepository eventCategoryMappingRepository;

    @Autowired
    private ProductMstRepository productMstRepository;

    private static final Logger logger = LoggerFactory.getLogger(EventService.class);

    @Transactional
    public EventMst createEvent(String userName, EventInput input, String email) {

        try {
            logger.info("Executing createEvent method with input as {} in class {} for User: {}", input, getClass().getName(), "");
            EventMst eventMst = new EventMst();
            String EventSharableId = UUID.randomUUID().toString();
//            System.out.println(EventSharableId);
            eventMst.setEventSharableId(EventSharableId);
            eventMst.setEventName(input.details().eventName());
            eventMst.setCreator(userName);
            eventMst.setAddress(input.location().address());
            eventMst.setDescription(input.details().eventDescription());
            eventMst.setOrganizerLink(input.organizer().organizerLink());
            eventMst.setOrganizerName(input.organizer().organizerName());
            eventMst.setOrganizerPhone(input.organizer().organizerPhone());
            eventMst.setOrganizerEmail(input.organizer().organizerEmail());
            eventMst.setTnc(input.details().eventTnc());
            eventMst.setImagePaths(input.imagePaths());
            eventMst.setFlagShowContactDetails(input.organizer().organizerCheck());
            eventMst.setEventDate(Date.valueOf(LocalDate.parse(input.date().eventDate(), DateTimeFormatter.ofPattern("yyyy-MM-dd"))));
            eventMst.setType(input.location().locationType());
            eventMst.setEventTimeZone(input.date().eventTimeZone());
            eventMst = eventMstRepository.save(eventMst);



            // Parse the times
            OffsetTime eventStartTime = OffsetTime.parse(input.date().eventStartTime(), DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneOffset.UTC));
            OffsetTime eventEndTime = OffsetTime.parse(input.date().eventEndTime(), DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneOffset.UTC));

            // Calculate the duration between the two times
            Duration duration = Duration.between(eventStartTime, eventEndTime);

            // Check if the duration is negative
            if (duration.isPositive()) {
                // Get the difference in hours, minutes, and seconds
                long hours = duration.toHours();
                long minutes = duration.toMinutes() % 60;
                long seconds = duration.getSeconds() % 60;

                // Format the duration as HH:MM:SS
                String formattedDuration = String.format("%02d:%02d:%02d", hours, minutes, seconds);
                eventMst.setEventDuration(formattedDuration);
            }
            eventMst.setEventStartTime(eventStartTime);

            ArrayList<Integer> categoryList = new ArrayList<>();
            for(CategoryIn categories : input.details().eventCategories()) {
                categoryList.add(categories.categoryId());
            }
            ArrayList<CategoryMst> categoryMsts = categoryMstRepository.findAllByCategoryMstIdIn(categoryList);
            Map<Integer, CategoryMst> categoryMap = categoryMsts.stream()
                    .collect(Collectors.toMap(
                            CategoryMst::getCategoryMstId, // Key extractor
                            categoryMst -> categoryMst,   // Value extractor
                            (existing, replacement) -> existing));

            ArrayList<EventCategoryMapping> eventCategoryMappingList = new ArrayList<>();

            for(CategoryIn category : input.details().eventCategories()) {
                CategoryMst categoryMst = new CategoryMst();
                EventCategoryMapping eventCategoryMapping = new EventCategoryMapping();
                if(!categoryMap.containsKey(category.categoryId())) {
                    categoryMst.setCategoryName(category.categoryName());
                    categoryMst = categoryMstRepository.save(categoryMst);
//                    categoryList.add(categoryMst);
                }else {
                    categoryMst = categoryMap.get(category.categoryId());
                }

                eventCategoryMapping.setEventId(eventMst.getEventMstId());
                eventCategoryMapping.setCategoryId(categoryMst.getCategoryMstId());
                eventCategoryMappingList.add(eventCategoryMapping);
            }
            eventCategoryMappingRepository.saveAll(eventCategoryMappingList);
//            categoryMstRepository.saveAll(categoryList);


            ArrayList<EventTicket> eventTicketsList = new ArrayList<>();
            for(TicketInput ticket : input.tickets()) {
                EventTicket eventTicket = new EventTicket();
                eventTicket.setEventId(eventMst.getEventMstId());
                eventTicket.setCurrency(ticket.ticketCurrency());
                eventTicket.setTicketName(ticket.ticketName());
                eventTicket.setPrice(ticket.ticketPrice());
                eventTicket.setRemainingTickets(ticket.ticketOnsale());
                eventTicket.setTotalTicketsOnSale(ticket.ticketOnsale());
                eventTicket.setTicketType(ticket.ticketType());
                eventTicket.setTicketsSold(0);
                eventTicket.setTicketDescription(ticket.ticketDescription());
                eventTicket.setTnc(eventMst.getTnc());
                eventTicketsList.add(eventTicket);
            }
            eventTicketsRepository.saveAll(eventTicketsList);

//            UserMst userMst = userMstRepository.findByUserName(userName);
//            ArrayList<UserEventPermissionMapping> userEventPermissionMappings = userEventPermissionMappingRepository.findAllByUserId(userMst.getUserMstId());

            UserEventPermissionMapping userEventPermissionMapping = new UserEventPermissionMapping();
//            userEventPermissionMapping.setEventRole(userMst.getRole());
            userEventPermissionMapping.setEventId(eventMst.getEventMstId());
            userEventPermissionMapping.setUserPhone(input.coOwner().coOwnerPhone());
            userEventPermissionMapping.setUserEmail(input.coOwner().coOwnerEmail());
            userEventPermissionMapping.setName(input.coOwner().coOwnerName());
            userEventPermissionMapping.setCoOwnerFlag(input.coOwner().coOwnerCheck() ? 1 : 0);
            userEventPermissionMapping.setEventRole(1);
            userEventPermissionMappingRepository.save(userEventPermissionMapping);
//            userEventPermissionMapping.setUserPhone(userMst.get);

            return eventMst;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Transactional
    public OnBoardingUrlDto createStripeAccount(String userName, String email) {
        try {

            OnBoardingUrlDto onBoardingUrlDto = new OnBoardingUrlDto();
            UserMst userMst = userMstRepository.findByUserEmail(email);
            String accountId = userMst.getConnectAccountId();
            if(accountId == null) {
                accountId = createConnectAccount(email).getId();
            }
            userMst.setConnectAccountId(accountId);
            userMstRepository.save(userMst);

            AccountLink accountLink = AccountLink.create(
                    AccountLinkCreateParams.builder()
                            .setAccount(accountId)
                            .setReturnUrl("https://dev.eventrocket.net/return/" + accountId)
                            .setRefreshUrl("https://dev.eventrocket.net/refresh/" + accountId)
                            .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                            .build()
            );
//            System.out.println("accountLink : " + accountLink);
            onBoardingUrlDto.setUrl(accountLink.getUrl());

            return onBoardingUrlDto;
        }
     catch (Exception e) {
        throw new RuntimeException(e);
    }
    }
    public Account createConnectAccount(String email) throws Exception {

        AccountCreateParams params =
                AccountCreateParams.builder()
                        .setCountry("US")
                        .setEmail(email)
                        .setController(
                                AccountCreateParams.Controller.builder()
                                        .setFees(
                                                AccountCreateParams.Controller.Fees.builder()
                                                        .setPayer(AccountCreateParams.Controller.Fees.Payer.APPLICATION)
                                                        .build()
                                        )
                                        .setLosses(
                                                AccountCreateParams.Controller.Losses.builder()
                                                        .setPayments(AccountCreateParams.Controller.Losses.Payments.APPLICATION)
                                                        .build()
                                        )
                                        .setStripeDashboard(
                                                AccountCreateParams.Controller.StripeDashboard.builder()
                                                        .setType(AccountCreateParams.Controller.StripeDashboard.Type.EXPRESS)
                                                        .build()
                                        )
                                        .build()
                        )
                        .build();

        return Account.create(params);
    }

    public StripeDetailsDto checkAccountExists(String userName,String email) {
        try {
           UserMst userMst = userMstRepository.findByUserEmail(email);
           StripeDetailsDto stripeDetailsDto = new StripeDetailsDto();
           if(userMst.getConnectAccountId() == null) {
                stripeDetailsDto.setIsOnboarded(false);
                return stripeDetailsDto;
           }
//            "acct_1Q4gIKPCEjHt2tBX"
            // Retrieve the connected account details
            Account account = Account.retrieve(userMst.getConnectAccountId());

            // Check the requirements object
            Account.Requirements requirements = account.getRequirements();

            // Check if there are any currently due requirements
            if (!requirements.getCurrentlyDue().isEmpty()) {
                stripeDetailsDto.setIsOnboarded(false);
                return stripeDetailsDto;
            }

            // Check if there are any past due requirements
            if (!requirements.getPastDue().isEmpty()) {
                stripeDetailsDto.setIsOnboarded(false);
                return stripeDetailsDto;
            }

            // Check if there are any errors in the requirements
            if (!requirements.getErrors().isEmpty()) {
                stripeDetailsDto.setIsOnboarded(false);
                return stripeDetailsDto;
            }

            // Check if there's a disabled reason
            if (requirements.getDisabledReason() != null) {
                stripeDetailsDto.setIsOnboarded(false);
                return stripeDetailsDto;
            }
            stripeDetailsDto.setIsOnboarded(true);
            return stripeDetailsDto;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Transactional
    public EventMst updateEvent (String userName, EventUpdate input, String email) {

        try {
            logger.info("Executing updateEvent method with input as {} in class {} for User: {}", input, getClass().getName(), email);
            EventMst eventMst = eventMstRepository.findByEventSharableId(input.eventSharableId());
//            EventMst eventMst = eventMstRepository.findByEventMstId(input.eventId());
            eventMst.setEventName(input.details().eventName());
            eventMst.setCreator(userName);
//            eventMst.setEventSharableId(input.eventSharableId());
            eventMst.setAddress(input.location().address());
            eventMst.setDescription(input.details().eventDescription());
            eventMst.setOrganizerLink(input.organizer().organizerLink());
            eventMst.setOrganizerName(input.organizer().organizerName());
            eventMst.setOrganizerPhone(input.organizer().organizerPhone());
            eventMst.setTnc(input.details().eventTnc());
            eventMst.setImagePaths(input.imagePaths());
            eventMst.setFlagShowContactDetails(input.organizer().organizerCheck());
            eventMst.setEventDate(Date.valueOf(LocalDate.parse(input.date().eventDate(), DateTimeFormatter.ofPattern("yyyy-MM-dd"))));
            eventMst.setType(input.location().locationType());
            eventMst.setEventTimeZone(input.date().eventTimeZone());
            eventMstRepository.save(eventMst);


            // Parse the times
            OffsetTime eventStartTime = OffsetTime.parse(input.date().eventStartTime(), DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneOffset.UTC));
            OffsetTime eventEndTime = OffsetTime.parse(input.date().eventEndTime(), DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneOffset.UTC));

            // Calculate the duration between the two times
            Duration duration = Duration.between(eventStartTime, eventEndTime);

            // Check if the duration is negative
            if (duration.isPositive()) {
                // Get the difference in hours, minutes, and seconds
                long hours = duration.toHours();
                long minutes = duration.toMinutes() % 60;
                long seconds = duration.getSeconds() % 60;

                // Format the duration as HH:MM:SS
                String formattedDuration = String.format("%02d:%02d:%02d", hours, minutes, seconds);
                eventMst.setEventDuration(formattedDuration);
            }
            eventMst.setEventStartTime(eventStartTime);

            ArrayList<Integer> categoryIdList = new ArrayList<>();
            for(CategoryIn category : input.details().eventCategories()) {
                categoryIdList.add(category.categoryId());
            }

            ArrayList<CategoryMst> categoryMsts = categoryMstRepository.findAllByCategoryMstIdIn(categoryIdList);
            Map<Integer, CategoryMst> categoryMap = categoryMsts.stream()
                    .collect(Collectors.toMap(
                            CategoryMst::getCategoryMstId, // Key extractor
                            categoryMst -> categoryMst,   // Value extractor
                            (existing, replacement) -> existing));

            ArrayList<EventCategoryMapping> eventCategories = eventCategoryMappingRepository.findAllByEventId(eventMst.getEventMstId());
            // Create a HashMap using the Stream API
            Map<Integer, EventCategoryMapping> categoryMappingMap = eventCategories.stream()
                    .collect(Collectors.toMap(
                            EventCategoryMapping::getCategoryId, // Key extractor
                            eventCategoryMapping -> eventCategoryMapping, // Value extractor
                            (existing, replacement) -> existing)); // Merge function (handles duplicates)


            ArrayList<EventCategoryMapping> eventCategoryMappingList = new ArrayList<>();
//            ArrayList<CategoryMst> categoryList = new ArrayList<>();
            for(CategoryIn category : input.details().eventCategories()) {
                CategoryMst categoryMst;
                EventCategoryMapping eventCategoryMapping;
                if(category.categoryId() != null && categoryMap.containsKey(category.categoryId())) {
                    categoryMst = categoryMap.get(category.categoryId());
//                    categoryMst.setUpdatedDate(ZonedDateTime.now().toOffsetDateTime());

                } else {
                    categoryMst = new CategoryMst();
                    categoryMst.setCategoryName(category.categoryName());
                    categoryMst = categoryMstRepository.save(categoryMst);
                }

                if(category.categoryId() != null && categoryMappingMap.containsKey(category.categoryId())) {
                    eventCategoryMapping = categoryMappingMap.get(category.categoryId());
                    eventCategoryMapping.setUpdatedDate(ZonedDateTime.now().toOffsetDateTime());
                }
                else {
                    eventCategoryMapping = new EventCategoryMapping();
                    eventCategoryMapping.setEventId(eventMst.getEventMstId());
                    eventCategoryMapping.setCategoryId(categoryMst.getCategoryMstId());
                }
                eventCategoryMappingList.add(eventCategoryMapping);
            }
            eventCategoryMappingRepository.saveAll(eventCategoryMappingList);
//            categoryMstRepository.saveAll(categoryList);

            ArrayList<EventTicket> eventTickets = eventTicketsRepository.findAllByEventId(eventMst.getEventMstId());
            Map<Integer, EventTicket> ticketMappingMap = eventTickets.stream()
                    .collect(Collectors.toMap(
                            EventTicket::getTicketId, // Key extractor
                            eventTicket -> eventTicket,  // Value extractor
                            (existing, replacement) -> existing));

            ArrayList<EventTicket> eventTicketsList = new ArrayList<>();

            for(TicketUpdate ticket : input.tickets()) {
                EventTicket eventTicket;
                if(ticket.ticketId() != null && ticketMappingMap.containsKey(ticket.ticketId())) {
                    eventTicket = ticketMappingMap.get(ticket.ticketId());
                    eventTicket.setCurrency(ticket.ticketCurrency());
                    eventTicket.setTicketName(ticket.ticketName());
                    eventTicket.setPrice(ticket.ticketPrice());
                    eventTicket.setTicketType(ticket.ticketType());
                    eventTicket.setTotalTicketsOnSale(ticket.ticketOnsale());
                    eventTicket.setRemainingTickets(ticket.ticketOnsale());
//                    eventTicket.setTicketsSold(ticket.ticketOnsale()-eventTicket.getRemainingTickets());
                    eventTicket.setTicketDescription(ticket.ticketDescription());
                    eventTicket.setTnc(eventMst.getTnc());
                    eventTicket.setUpdatedDate(ZonedDateTime.now().toOffsetDateTime());

                }
                else {
                    eventTicket = new EventTicket();
                    eventTicket.setCurrency(ticket.ticketCurrency());
                    eventTicket.setTicketName(ticket.ticketName());
                    eventTicket.setPrice(ticket.ticketPrice());
                    eventTicket.setTotalTicketsOnSale(ticket.ticketOnsale());
                    eventTicket.setRemainingTickets(ticket.ticketOnsale());
                    eventTicket.setTicketsSold(ticket.ticketOnsale()-eventTicket.getRemainingTickets());
                    eventTicket.setTicketDescription(ticket.ticketDescription());
                    eventTicket.setTnc(eventMst.getTnc());
                    eventTicket.setTicketType(ticket.ticketType());
                }

                eventTicketsList.add(eventTicket);
            }

            eventTicketsRepository.saveAll(eventTicketsList);
//            UserMst userMst = userMstRepository.findByUserName(userName);
            UserEventPermissionMapping userEventPermissionMapping = userEventPermissionMappingRepository.findByEventId(eventMst.getEventMstId());
            userEventPermissionMapping.setUserPhone(input.coOwner().coOwnerPhone());
            userEventPermissionMapping.setUserEmail(input.coOwner().coOwnerEmail());
            userEventPermissionMapping.setName(input.coOwner().coOwnerName());
            userEventPermissionMapping.setUpdatedDate(ZonedDateTime.now().toOffsetDateTime());
            userEventPermissionMapping.setCoOwnerFlag(input.coOwner().coOwnerCheck() ? 1 : 0);
            userEventPermissionMapping.setEventRole(1);
//            userEventPermissionMappingRepository.save(userEventPermissionMapping);
            return eventMst;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public EventDetailsDto getEventDetailsById(String userName, EventDetailsInput input, String email) {
        try {
            EventDetailsDto eventDetailsDto = new EventDetailsDto();
            EventMst eventMst = eventMstRepository.findByEventSharableId(input.eventSharableId());
            eventDetailsDto.setImagePaths(eventMst.getImagePaths());
            ArrayList<EventTicket> eventTickets = eventTicketsRepository.findAllByEventId(eventMst.getEventMstId());
            ArrayList<EventCategoryMapping> eventCategoryMappings = eventCategoryMappingRepository.findAllByEventId(eventMst.getEventMstId());

            ArrayList<Integer> categoryIdList = new ArrayList<>();
            DetailsUpdateDto detailsUpdateDto = new DetailsUpdateDto();

            detailsUpdateDto.setEventName(eventMst.getEventName());
            detailsUpdateDto.setEventDescription(eventMst.getDescription());
            detailsUpdateDto.setEventTnc(eventMst.getTnc());


            for(EventCategoryMapping eventCategoryMapping : eventCategoryMappings) {
                categoryIdList.add(eventCategoryMapping.getCategoryId());
            }
            ArrayList<CategoryMst> categoryMsts = categoryMstRepository.findAllByCategoryMstIdIn(categoryIdList);
            ArrayList<CategoryIn> categoryMstDtoArrayList = new ArrayList<>();
            for(CategoryMst categoryMst : categoryMsts) {
                CategoryMstDto categoryMstDto = new CategoryMstDto();
                categoryMstDto.setCategoryName(categoryMst.getCategoryName());
                categoryMstDto.setCategoryId(categoryMst.getCategoryMstId());
                categoryMstDtoArrayList.add(new CategoryIn(categoryMst.getCategoryMstId(),categoryMst.getCategoryName()));
            }
            detailsUpdateDto.setEventCategories(categoryMstDtoArrayList);

            ArrayList<TicketDto> ticketDtos = new ArrayList<>();
             for(EventTicket  evetTicket : eventTickets) {
                 TicketDto ticketDto = new TicketDto();
                 ticketDto.setTicketId(evetTicket.getTicketId());
                 ticketDto.setTicketCurrency(evetTicket.getCurrency());
                 ticketDto.setTicketDescription(evetTicket.getTicketDescription());
                 ticketDto.setTicketOnsale(evetTicket.getTotalTicketsOnSale());
                 ticketDto.setTicketPrice(evetTicket.getPrice());
                 ticketDto.setTicketName(evetTicket.getTicketName());
                 ticketDto.setTicketType(evetTicket.getTicketType());
                 ticketDtos.add(ticketDto);
             }
             LocationDto locationDto = new LocationDto();
             locationDto.setAddress(eventMst.getAddress());
            locationDto.setLocationType(eventMst.getType());

             OrganizerDto organizerDto = new OrganizerDto();
             organizerDto.setOrganizerLink(eventMst.getOrganizerLink());
             organizerDto.setOrganizerName(eventMst.getOrganizerName());
             organizerDto.setOrganizerPhone(eventMst.getOrganizerPhone());
             organizerDto.setOrganizerEmail(eventMst.getOrganizerEmail());
             organizerDto.setOrganizerCheck(eventMst.getFlagShowContactDetails());



             EventDateDto date = new EventDateDto();
            date.setEventDate(eventMst.getEventDate());
            date.setEventStartTime(eventMst.getEventStartTime());
            date.setEventTimeZone(eventMst.getEventTimeZone());
            if(eventMst.getEventDuration() != null) {
                String[] timeParts = eventMst.getEventDuration().split(":");

                // Parse hours, minutes, and seconds
                int hours = Integer.parseInt(timeParts[0]);
                int minutes = Integer.parseInt(timeParts[1]);
                int seconds = Integer.parseInt(timeParts[2]);
                date.setEventDuration((hours * 3600 + minutes * 60 + seconds));

            }

//             UserMst userMst = userMstRepository.findByUserName(userName);
             UserEventPermissionMapping userEventPermissionMapping = userEventPermissionMappingRepository.findByEventId(eventMst.getEventMstId());

             CoOwnerDto coOwnerDto = new CoOwnerDto();
             coOwnerDto.setCoOwnerEmail(userEventPermissionMapping.getUserEmail());
             coOwnerDto.setCoOwnerName(userEventPermissionMapping.getName());
             coOwnerDto.setCoOwnerPhone(userEventPermissionMapping.getUserPhone());
//             coOwnerDto.setCoOwnerCheck(userEventPermissionMapping.()));

//             eventDetailsDto.set(eventMst.getEventName());
//             eventDetailsDto.setEventId(eventMst.getEventMstId());
             eventDetailsDto.setDetails(detailsUpdateDto);
             eventDetailsDto.setEventSharableId(eventMst.getEventSharableId());
//             eventDetailsDto.setTnc(eventMst.getTnc());
             eventDetailsDto.setLocation(locationDto);
             eventDetailsDto.setOrganizer(organizerDto);
             eventDetailsDto.setCoOwner(coOwnerDto);
             eventDetailsDto.setTickets(ticketDtos);
             eventDetailsDto.setDate(date);

             return eventDetailsDto;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public EventDetailsDto viewEvent(ViewDetailsInput input) {
        try {

            EventDetailsDto eventDetailsDto = new EventDetailsDto();
            EventMst eventMst = eventMstRepository.findByEventSharableId(input.eventSharableId());
            eventDetailsDto.setImagePaths(eventMst.getImagePaths());
            ArrayList<EventTicket> eventTickets = eventTicketsRepository.findAllByEventId(eventMst.getEventMstId());
            ArrayList<EventCategoryMapping> eventCategoryMappings = eventCategoryMappingRepository.findAllByEventId(eventMst.getEventMstId());

            ArrayList<Integer> categoryIdList = new ArrayList<>();
            DetailsUpdateDto detailsUpdateDto = new DetailsUpdateDto();

            detailsUpdateDto.setEventName(eventMst.getEventName());
            detailsUpdateDto.setEventDescription(eventMst.getDescription());
            detailsUpdateDto.setEventTnc(eventMst.getTnc());
            for(EventCategoryMapping eventCategoryMapping : eventCategoryMappings) {
                categoryIdList.add(eventCategoryMapping.getCategoryId());
            }
            ArrayList<CategoryMst> categoryMsts = categoryMstRepository.findAllByCategoryMstIdIn(categoryIdList);
            ArrayList<CategoryIn> categoryMstDtoArrayList = new ArrayList<>();
            for(CategoryMst categoryMst : categoryMsts) {
                CategoryMstDto categoryMstDto = new CategoryMstDto();
                categoryMstDto.setCategoryName(categoryMst.getCategoryName());
                categoryMstDto.setCategoryId(categoryMst.getCategoryMstId());
                categoryMstDtoArrayList.add(new CategoryIn(categoryMst.getCategoryMstId(),categoryMst.getCategoryName()));
            }
            detailsUpdateDto.setEventCategories(categoryMstDtoArrayList);

            ArrayList<TicketDto> ticketDtos = new ArrayList<>();
            for(EventTicket  evetTicket : eventTickets) {
                TicketDto ticketDto = new TicketDto();
                ticketDto.setTicketId(evetTicket.getTicketId());
                ticketDto.setTicketCurrency(evetTicket.getCurrency());
                ticketDto.setTicketDescription(evetTicket.getTicketDescription());
                ticketDto.setTicketOnsale(evetTicket.getTotalTicketsOnSale());
                ticketDto.setTicketPrice(evetTicket.getPrice());
                ticketDto.setTicketName(evetTicket.getTicketName());
                ticketDto.setTicketType(evetTicket.getTicketType());
                ticketDtos.add(ticketDto);
            }

            EventDateDto date = new EventDateDto();
            date.setEventDate(eventMst.getEventDate());
            date.setEventStartTime(eventMst.getEventStartTime());
            date.setEventTimeZone(eventMst.getEventTimeZone());
            if(eventMst.getEventDuration() != null) {
                String[] timeParts = eventMst.getEventDuration().split(":");

                // Parse hours, minutes, and seconds
                int hours = Integer.parseInt(timeParts[0]);
                int minutes = Integer.parseInt(timeParts[1]);
                int seconds = Integer.parseInt(timeParts[2]);
                date.setEventDuration((hours * 3600 + minutes * 60 + seconds));

            }
            LocationDto locationDto = new LocationDto();
            locationDto.setAddress(eventMst.getAddress());
            locationDto.setLocationType(eventMst.getType());

            OrganizerDto organizerDto = new OrganizerDto();
            organizerDto.setOrganizerLink(eventMst.getOrganizerLink());

            if(eventMst.getFlagShowContactDetails() != null && eventMst.getFlagShowContactDetails()) {
                organizerDto.setOrganizerEmail(eventMst.getOrganizerEmail());
                organizerDto.setOrganizerPhone(eventMst.getOrganizerPhone());
                organizerDto.setOrganizerCheck(true);
            }
            else {
                organizerDto.setOrganizerCheck(false);
            }
            organizerDto.setOrganizerName(eventMst.getOrganizerName());


            EventDateDto eventDateDto = new EventDateDto();
            eventDateDto.setEventDate(eventMst.getEventDate());
            eventDateDto.setEventStartTime(eventDateDto.getEventStartTime());


//             UserMst userMst = userMstRepository.findByUserName(userName);
            UserEventPermissionMapping userEventPermissionMapping = userEventPermissionMappingRepository.findByEventId(eventMst.getEventMstId());

            CoOwnerDto coOwnerDto = new CoOwnerDto();
            if(userEventPermissionMapping.getCoOwnerFlag() != null && userEventPermissionMapping.getCoOwnerFlag() == 1) {
            coOwnerDto.setCoOwnerEmail(userEventPermissionMapping.getUserEmail());
            coOwnerDto.setCoOwnerPhone(userEventPermissionMapping.getUserPhone());
            coOwnerDto.setCoOwnerCheck(true);
            }
            else {
                coOwnerDto.setCoOwnerCheck(false);
            }
            coOwnerDto.setCoOwnerName(userEventPermissionMapping.getName());

//            eventDetailsDto.setEventName(eventMst.getEventName());
//            eventDetailsDto.setEventId(eventMst.getEventMstId());
            eventDetailsDto.setDetails(detailsUpdateDto);
//            eventDetailsDto.setEventSharableId(eventMst.getEventSharableId());
//            eventDetailsDto.setTnc(eventMst.getTnc());
            eventDetailsDto.setLocation(locationDto);
            eventDetailsDto.setOrganizer(organizerDto);
            eventDetailsDto.setCoOwner(coOwnerDto);
            eventDetailsDto.setTickets(ticketDtos);
            eventDetailsDto.setDate(date);
            eventDetailsDto.setIsMyEvent(input.email() != null && input.email().equals(eventMst.getOrganizerEmail()));
            return eventDetailsDto;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public CategoryListDto getCategories(String userName,  String email) {
        try {
            CategoryListDto categoryListDto = new CategoryListDto();
             Iterable<CategoryMst> categoryMstList = categoryMstRepository.findAll();
             ArrayList<CategoryMstDto> categoryMstDtos = new ArrayList<>();
             for(CategoryMst categoryMst : categoryMstList) {
                 CategoryMstDto categoryMstDto = new CategoryMstDto();
                 categoryMstDto.setCategoryId(categoryMst.getCategoryMstId());
                 categoryMstDto.setCategoryName(categoryMst.getCategoryName());
                 categoryMstDtos.add(categoryMstDto);
             }
             categoryListDto.setCategoryList(categoryMstDtos);
             return categoryListDto;
        }
        catch(Exception e) {
            throw new RuntimeException(e);
        }
    }
    public TopEventsResponsesDto getTopEvents(PageInput input) {
        try {
            // Step 1: Fetch all entries with isActive = 1
            ArrayList<EventMst> activeEvents = eventMstRepository.findAllByIsActive(1);

            // Step 2: Get current timestamp in the default time zone
            ZonedDateTime currentDateTime = ZonedDateTime.now();

            // Step 3: Iterate through the list of events and compare their event time with the current time
            for (EventMst event : activeEvents) {
                // Combine event_date, event_time, and event_time_zone into ZonedDateTime
                LocalDate eventDate = (event.getEventDate()).toLocalDate(); // assuming eventDate is of type LocalDate
                // Assuming eventTime is of type OffsetTime
                LocalTime eventTime = ((event.getEventStartTime() != null)
                        ? event.getEventStartTime()
                        : OffsetTime.of(0, 0, 0, 0, ZoneOffset.UTC)).toLocalTime(); // Default to 00:00:00 UTC if null

                // Check if timeZone is null, if so, use the default value "UTC"
                String timeZone = (event.getEventTimeZone() != null) ? event.getEventTimeZone() : "UTC"; // assuming timeZone is stored as a string like "UTC", "America/New_York", etc.

                // Create the event's ZonedDateTime from its components
                ZonedDateTime eventDateTime = ZonedDateTime.of(eventDate, eventTime, ZoneId.of(timeZone));

                // Step 4: Compare the event time with the current timestamp
                if (eventDateTime.isBefore(currentDateTime)) {
                    // If the event has already passed, set isActive to 0
                    event.setIsActive(0);
                }
            }

            // Step 5: Save the updated events back to the database
            eventMstRepository.saveAll(activeEvents);
            // Create Sort object based on input
            Sort sort = input.sortOrder().equalsIgnoreCase("ASC")
                    ? Sort.by(input.sortField()).ascending().and(Sort.by("eventMstId").descending())
                    : Sort.by(input.sortField()).descending().and(Sort.by("eventMstId").descending());

            // Create Pageable object
            Pageable pageable = PageRequest.of(input.pageNumber(), input.pageSize(), sort);

            // Build the specification for filtering
            LocalDate firstDate = (input.date() != null && input.date().firstDate() != null) ? LocalDate.parse(input.date().firstDate(), DateTimeFormatter.ofPattern("yyyy-MM-dd")) : null;
            LocalDate lastDate = (input.date() != null && input.date().lastDate() != null) ? LocalDate.parse(input.date().lastDate(), DateTimeFormatter.ofPattern("yyyy-MM-dd")) : null;
            Specification<EventMst> specification = Specification
                    .where(EventSpecification.hasSearchInput(input.searchInput()))
                    .and(EventSpecification.hasEventDate(firstDate,lastDate)) // Pass null if date is null
                    .and(EventSpecification.hasFormat(input.format()))
                    .and(EventSpecification.isActive())
                    .and(EventSpecification.hasPriceRange(
                            input.priceRange() != null ? input.priceRange().lowestPrice() : null,
                            input.priceRange() != null ? input.priceRange().highestPrice() : null))
                    .and(EventSpecification.hasCategory(input.category() == null ? -1 : input.category()));

            // Fetch paginated events using specification
            Page<EventMst> eventPage = eventMstRepository.findAll(specification, pageable);

            // Map to TopEventsResponsesDto
            TopEventsResponsesDto topEventsResponsesDto = new TopEventsResponsesDto();
            ArrayList<TopEventsDto> topEventsDtos = new ArrayList<>();

            for (EventMst eventMst : eventPage) {
                TopEventsDto topEventsDto = new TopEventsDto();
                ArrayList<EventTicket> eventTickets = eventTicketsRepository.findAllByEventId(eventMst.getEventMstId());
                double maxTicketPrice = Double.MIN_VALUE;  // Initialize to the smallest possible value
                double minTicketPrice = Double.MAX_VALUE;  // Initialize to the largest possible value

                for (EventTicket eventTicket : eventTickets) {
                    if (eventTicket.getPrice() != null) {
                        maxTicketPrice = Math.max(maxTicketPrice, eventTicket.getPrice());
                        minTicketPrice = Math.min(minTicketPrice, eventTicket.getPrice());
                    }
                }

                if (minTicketPrice == Double.MAX_VALUE) {
                    minTicketPrice = 0.0;  // or set it to a default value, as needed
                }
                if (maxTicketPrice == Double.MIN_VALUE) {
                    maxTicketPrice = 0.0;  // or set it to a default value, as needed
                }
                topEventsDto.setMinTicketPrice(minTicketPrice);
                topEventsDto.setMaxTicketPrice(maxTicketPrice);
                topEventsDto.setImagePaths(eventMst.getImagePaths());
                topEventsDto.setAddress(eventMst.getAddress());
                topEventsDto.setEventDate(eventMst.getEventDate().toString());
                topEventsDto.setEventStartTime(eventMst.getEventStartTime().toString());
                topEventsDto.setEventSharableId(eventMst.getEventSharableId());
                topEventsDto.setEventName(eventMst.getEventName());
                topEventsDto.setTimeZone(eventMst.getEventTimeZone());
                topEventsDtos.add(topEventsDto);
            }

            topEventsResponsesDto.setTopEvents(topEventsDtos);
            topEventsResponsesDto.setHasNext(eventPage.hasNext());
            topEventsResponsesDto.setHasPrevious(eventPage.hasPrevious());
            topEventsResponsesDto.setTotalRows(eventPage.getTotalElements());

            return topEventsResponsesDto;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}