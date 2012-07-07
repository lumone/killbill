/*
 * Copyright 2010-2011 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ning.billing.beatrix.integration;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.ning.billing.account.api.Account;
import com.ning.billing.api.TestApiListener.NextEvent;
import com.ning.billing.catalog.api.BillingPeriod;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.catalog.api.PhaseType;
import com.ning.billing.catalog.api.PlanPhaseSpecifier;
import com.ning.billing.catalog.api.PriceListSet;
import com.ning.billing.catalog.api.ProductCategory;
import com.ning.billing.entitlement.api.user.SubscriptionBundle;
import com.ning.billing.entitlement.api.user.SubscriptionData;
import com.ning.billing.invoice.api.Invoice;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

@Test(groups = "slow")
@Guice(modules = {BeatrixModule.class})
public class TestIntegration extends TestIntegrationBase {
    @Test(groups = "slow", enabled = true)
    public void testBasePlanCompleteWithBillingDayInPast() throws Exception {
        log.info("Starting testBasePlanCompleteWithBillingDayInPast");
        final DateTime startDate = new DateTime(2012, 2, 1, 0, 3, 42, 0);
        testBasePlanComplete(startDate, 31, false);
    }

    @Test(groups = "slow", enabled = true)
    public void testBasePlanCompleteWithBillingDayPresent() throws Exception {
        log.info("Starting testBasePlanCompleteWithBillingDayPresent");
        final DateTime startDate = new DateTime(2012, 2, 1, 0, 3, 42, 0);
        testBasePlanComplete(startDate, 1, false);
    }

    @Test(groups = "slow", enabled = true)
    public void testBasePlanCompleteWithBillingDayAlignedWithTrial() throws Exception {
        log.info("Starting testBasePlanCompleteWithBillingDayAlignedWithTrial");
        final DateTime startDate = new DateTime(2012, 2, 1, 0, 3, 42, 0);
        testBasePlanComplete(startDate, 2, false);
    }

    @Test(groups = "slow", enabled = true)
    public void testBasePlanCompleteWithBillingDayInFuture() throws Exception {
        log.info("Starting testBasePlanCompleteWithBillingDayInFuture");
        final DateTime startDate = new DateTime(2012, 2, 1, 0, 3, 42, 0);
        testBasePlanComplete(startDate, 3, true);
    }

    @Test(groups = {"slow", "stress"}, enabled = false)
    public void stressTest() throws Exception {
        final int maxIterations = 7;
        for (int curIteration = 0; curIteration < maxIterations; curIteration++) {
            log.info("################################  ITERATION " + curIteration + "  #########################");
            Thread.sleep(1000);
            setupTest();
            testBasePlanCompleteWithBillingDayPresent();
            Thread.sleep(1000);
            setupTest();
            testBasePlanCompleteWithBillingDayInPast();
            Thread.sleep(1000);
            setupTest();
            testBasePlanCompleteWithBillingDayAlignedWithTrial();
            Thread.sleep(1000);
            setupTest();
            testBasePlanCompleteWithBillingDayInFuture();
        }
    }


    @Test(groups = {"slow", "stress"}, enabled = true)
    public void stressTestDebug() throws Exception {
        final int maxIterations = 5;
        for (int curIteration = 0; curIteration < maxIterations; curIteration++) {
            log.info("################################  ITERATION " + curIteration + "  #########################");
            Thread.sleep(1000);
            testRepairChangeBPWithAddonIncluded();
            setupTest();
        }
    }


    @Test(groups = "slow", enabled = true)
    public void testRepairChangeBPWithAddonIncluded() throws Exception {

        log.info("Starting testRepairChangeBPWithAddonIncluded");

        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 13, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithPaymentMethod(getAccountData(25));
        assertNotNull(account);

        final SubscriptionBundle bundle = entitlementUserApi.createBundleForAccount(account.getId(), "whatever", context);

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

        busHandler.pushExpectedEvent(NextEvent.CREATE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        final SubscriptionData baseSubscription = subscriptionDataFromSubscription(entitlementUserApi.createSubscription(bundle.getId(),
                                                                                                                   new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, planSetName, null), null, context));
        assertNotNull(baseSubscription);
        assertTrue(busHandler.isCompleted(DELAY));
        assertListenerStatus();

        // MOVE CLOCK A LITTLE BIT-- STILL IN TRIAL
        final Interval it = new Interval(clock.getUTCNow(), clock.getUTCNow().plusDays(3));
        log.info("Moving clock from" + clock.getUTCNow() + " to " + clock.getUTCNow().plusDays(3));
        clock.addDays(3);

        busHandler.pushExpectedEvent(NextEvent.CREATE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        busHandler.pushExpectedEvent(NextEvent.PAYMENT);
        subscriptionDataFromSubscription(entitlementUserApi.createSubscription(bundle.getId(),
                                                                               new PlanPhaseSpecifier("Telescopic-Scope", ProductCategory.ADD_ON, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null), null, context));
        assertTrue(busHandler.isCompleted(DELAY));
        assertListenerStatus();


        busHandler.pushExpectedEvent(NextEvent.CREATE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        busHandler.pushExpectedEvent(NextEvent.PAYMENT);
        final SubscriptionData aoSubscription2 = subscriptionDataFromSubscription(entitlementUserApi.createSubscription(bundle.getId(),
                                                                                                                  new PlanPhaseSpecifier("Laser-Scope", ProductCategory.ADD_ON, BillingPeriod.MONTHLY, PriceListSet.DEFAULT_PRICELIST_NAME, null), null, context));
        assertTrue(busHandler.isCompleted(DELAY));
        assertListenerStatus();

        // MOVE CLOCK A LITTLE BIT MORE -- EITHER STAY IN TRIAL OR GET OUT
        busHandler.pushExpectedEvent(NextEvent.PHASE);
        busHandler.pushExpectedEvent(NextEvent.PHASE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        busHandler.pushExpectedEvent(NextEvent.PAYMENT);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        log.info("Moving clock from" + clock.getUTCNow() + " to " + clock.getUTCNow().plusDays(28));
        clock.addDays(28);// 26 / 5
        assertTrue(busHandler.isCompleted(DELAY));
        assertListenerStatus();

        busHandler.pushExpectedEvent(NextEvent.PHASE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        busHandler.pushExpectedEvent(NextEvent.PAYMENT);
        log.info("Moving clock from" + clock.getUTCNow() + " to " + clock.getUTCNow().plusDays(3));
        clock.addDays(3);// 29 / 5
        assertTrue(busHandler.isCompleted(DELAY));
        assertListenerStatus();

        log.info("Moving clock from" + clock.getUTCNow() + " to " + clock.getUTCNow().plusDays(10));
        clock.addDays(10);// 8 / 6
        assertTrue(busHandler.isCompleted(DELAY));
        assertListenerStatus();

        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        busHandler.pushExpectedEvent(NextEvent.PAYMENT);
        log.info("Moving clock from" + clock.getUTCNow() + " to " + clock.getUTCNow().plusDays(18));
        clock.addDays(18);// 26 / 6
        assertTrue(busHandler.isCompleted(DELAY));
        assertListenerStatus();

        log.info("Moving clock from" + clock.getUTCNow() + " to " + clock.getUTCNow().plusDays(3));
        clock.addDays(3);
        assertTrue(busHandler.isCompleted(DELAY));
        assertListenerStatus();
    }


    @Test(groups = {"slow"})
    public void testRepairForInvoicing() throws Exception {

        log.info("Starting testRepairForInvoicing");

        final Account account = createAccountWithPaymentMethod(getAccountData(1));
        final UUID accountId = account.getId();
        assertNotNull(account);

        final DateTime initialDate = new DateTime(2012, 4, 25, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());

        final SubscriptionBundle bundle = entitlementUserApi.createBundleForAccount(account.getId(), "someBundle", context);
        assertNotNull(bundle);

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;
        entitlementUserApi.createSubscription(bundle.getId(),
                                              new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, planSetName, null), null, context);

        busHandler.reset();
        busHandler.pushExpectedEvent(NextEvent.CREATE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        assertTrue(busHandler.isCompleted(DELAY));

        final List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(accountId);
        assertEquals(invoices.size(), 1);

        // TODO: Jeff implement repair
    }

    @Test(groups = "slow", enabled = true)
    public void testWithRecreatePlan() throws Exception {

        log.info("Starting testWithRecreatePlan");

        final DateTime initialDate = new DateTime(2012, 2, 1, 0, 3, 42, 0);
        final int billingDay = 2;

        log.info("Beginning test with BCD of " + billingDay);
        final Account account = createAccountWithPaymentMethod(getAccountData(billingDay));
        final UUID accountId = account.getId();
        assertNotNull(account);

        // set clock to the initial start date
        clock.setDeltaFromReality(initialDate.getMillis() - clock.getUTCNow().getMillis());
        final SubscriptionBundle bundle = entitlementUserApi.createBundleForAccount(account.getId(), "whatever2", context);

        String productName = "Shotgun";
        BillingPeriod term = BillingPeriod.MONTHLY;
        String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE NextEvent.INVOICE
        //
        busHandler.pushExpectedEvent(NextEvent.CREATE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);

        SubscriptionData subscription = subscriptionDataFromSubscription(entitlementUserApi.createSubscription(bundle.getId(),
                                                                                                               new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, planSetName, null), null, context));

        assertNotNull(subscription);
        assertTrue(busHandler.isCompleted(DELAY));

        //
        // VERIFY CTD HAS BEEN SET
        //
        final DateTime startDate = subscription.getCurrentPhaseStart();
        DateTime endDate = startDate.plusDays(30);
        final BigDecimal rate = subscription.getCurrentPhase().getFixedPrice().getPrice(Currency.USD);
        final int invoiceItemCount = 1;
        verifyTestResult(accountId, subscription.getId(), startDate, endDate, rate, endDate, invoiceItemCount);

        //
        // MOVE TIME TO AFTER TRIAL AND EXPECT BOTH EVENTS :  NextEvent.PHASE NextEvent.INVOICE
        //
        busHandler.pushExpectedEvent(NextEvent.PHASE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        busHandler.pushExpectedEvent(NextEvent.PAYMENT);
        clock.addDeltaFromReality(AT_LEAST_ONE_MONTH_MS);
        assertTrue(busHandler.isCompleted(DELAY));

        subscription = subscriptionDataFromSubscription(entitlementUserApi.getSubscriptionFromId(subscription.getId()));
        subscription.cancel(clock.getUTCNow(), false, context);

        // MOVE AFTER CANCEL DATE AND EXPECT EVENT : NextEvent.CANCEL
        busHandler.pushExpectedEvent(NextEvent.CANCEL);
        endDate = subscription.getChargedThroughDate();
        final Interval it = new Interval(clock.getUTCNow(), endDate);
        clock.addDeltaFromReality(it.toDurationMillis());
        assertTrue(busHandler.isCompleted(DELAY));

        productName = "Assault-Rifle";
        term = BillingPeriod.MONTHLY;
        planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

        busHandler.pushExpectedEvent(NextEvent.RE_CREATE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        busHandler.pushExpectedEvent(NextEvent.PAYMENT);
        subscription.recreate(new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, planSetName, null), endDate, context);
        assertTrue(busHandler.isCompleted(DELAY));

        assertListenerStatus();
    }

    private void testBasePlanComplete(final DateTime initialCreationDate, final int billingDay,
                                      final boolean proRationExpected) throws Exception {

        log.info("Beginning test with BCD of " + billingDay);
        final Account account = createAccountWithPaymentMethod(getAccountData(billingDay));
        final UUID accountId = account.getId();

        // set clock to the initial start date
        clock.setDeltaFromReality(initialCreationDate.getMillis() - clock.getUTCNow().getMillis());
        final SubscriptionBundle bundle = entitlementUserApi.createBundleForAccount(account.getId(), "whatever", context);

        final String productName = "Shotgun";
        final BillingPeriod term = BillingPeriod.MONTHLY;
        final String planSetName = PriceListSet.DEFAULT_PRICELIST_NAME;

        //
        // CREATE SUBSCRIPTION AND EXPECT BOTH EVENTS: NextEvent.CREATE NextEvent.INVOICE
        //
        busHandler.pushExpectedEvent(NextEvent.CREATE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        SubscriptionData subscription = subscriptionDataFromSubscription(entitlementUserApi.createSubscription(bundle.getId(),
                                                                                                               new PlanPhaseSpecifier(productName, ProductCategory.BASE, term, planSetName, null), null, context));
        assertNotNull(subscription);
        assertTrue(busHandler.isCompleted(DELAY));

        //
        // VERIFY CTD HAS BEEN SET
        //
        DateTime startDate = subscription.getCurrentPhaseStart();
        DateTime endDate = startDate.plusDays(30);
        BigDecimal rate = subscription.getCurrentPhase().getFixedPrice().getPrice(Currency.USD);
        int invoiceItemCount = 1;
        verifyTestResult(accountId, subscription.getId(), startDate, endDate, rate, endDate, invoiceItemCount);

        //
        // CHANGE PLAN IMMEDIATELY AND EXPECT BOTH EVENTS: NextEvent.CHANGE NextEvent.INVOICE
        //
        busHandler.pushExpectedEvent(NextEvent.CHANGE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);

        BillingPeriod newTerm = BillingPeriod.MONTHLY;
        String newPlanSetName = PriceListSet.DEFAULT_PRICELIST_NAME;
        String newProductName = "Assault-Rifle";
        subscription.changePlan(newProductName, newTerm, newPlanSetName, clock.getUTCNow(), context);

        assertTrue(busHandler.isCompleted(DELAY));

        //
        // VERIFY AGAIN CTD HAS BEEN SET
        //
        startDate = subscription.getCurrentPhaseStart();
        endDate = startDate.plusDays(30);
        invoiceItemCount = 2;
        verifyTestResult(accountId, subscription.getId(), startDate, endDate, rate, endDate, invoiceItemCount);

        //
        // MOVE TIME TO AFTER TRIAL AND EXPECT BOTH EVENTS :  NextEvent.PHASE NextEvent.INVOICE
        //
        busHandler.pushExpectedEvent(NextEvent.PHASE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        busHandler.pushExpectedEvent(NextEvent.PAYMENT);

        if (proRationExpected) {
            busHandler.pushExpectedEvent(NextEvent.INVOICE);
            busHandler.pushExpectedEvent(NextEvent.PAYMENT);
        }

        clock.addDeltaFromReality(AT_LEAST_ONE_MONTH_MS);

        // STEPH
        /*
        Thread.sleep(6000000);
        */
        assertTrue(busHandler.isCompleted(DELAY));

        startDate = subscription.getCurrentPhaseStart();
        rate = subscription.getCurrentPhase().getRecurringPrice().getPrice(Currency.USD);
        BigDecimal price;
        final DateTime chargeThroughDate;

        switch (billingDay) {
            case 1:
                // this will result in a 30-day pro-ration
                price = THIRTY.divide(THIRTY_ONE, 2 * NUMBER_OF_DECIMALS, ROUNDING_METHOD).multiply(rate).setScale(NUMBER_OF_DECIMALS, ROUNDING_METHOD);
                chargeThroughDate = startDate.plusMonths(1).toMutableDateTime().dayOfMonth().set(billingDay).toDateTime();
                invoiceItemCount += 1;
                verifyTestResult(accountId, subscription.getId(), startDate, chargeThroughDate, price, chargeThroughDate, invoiceItemCount);
                break;
            case 2:
                // this will result in one full-period invoice item
                price = rate;
                chargeThroughDate = startDate.plusMonths(1);
                invoiceItemCount += 1;
                verifyTestResult(accountId, subscription.getId(), startDate, chargeThroughDate, price, chargeThroughDate, invoiceItemCount);
                break;
            case 3:
                // this will result in a 1-day leading pro-ration and a full-period invoice item
                price = ONE.divide(TWENTY_NINE, 2 * NUMBER_OF_DECIMALS, ROUNDING_METHOD).multiply(rate).setScale(NUMBER_OF_DECIMALS, ROUNDING_METHOD);
                final DateTime firstEndDate = startDate.plusDays(1);
                chargeThroughDate = firstEndDate.plusMonths(1);
                invoiceItemCount += 2;
                verifyTestResult(accountId, subscription.getId(), startDate, firstEndDate, price, chargeThroughDate, invoiceItemCount);
                verifyTestResult(accountId, subscription.getId(), firstEndDate, chargeThroughDate, rate, chargeThroughDate, invoiceItemCount);
                break;
            case 31:
                // this will result in a 29-day pro-ration
                chargeThroughDate = startDate.toMutableDateTime().dayOfMonth().set(31).toDateTime();
                price = TWENTY_NINE.divide(THIRTY_ONE, 2 * NUMBER_OF_DECIMALS, ROUNDING_METHOD).multiply(rate).setScale(NUMBER_OF_DECIMALS, ROUNDING_METHOD);
                invoiceItemCount += 1;
                verifyTestResult(accountId, subscription.getId(), startDate, chargeThroughDate, price, chargeThroughDate, invoiceItemCount);
                break;
            default:
                throw new UnsupportedOperationException();
        }

        //
        // CHANGE PLAN EOT AND EXPECT NOTHING
        //
        newTerm = BillingPeriod.MONTHLY;
        newPlanSetName = PriceListSet.DEFAULT_PRICELIST_NAME;
        newProductName = "Pistol";
        subscription = subscriptionDataFromSubscription(entitlementUserApi.getSubscriptionFromId(subscription.getId()));
        subscription.changePlan(newProductName, newTerm, newPlanSetName, clock.getUTCNow(), context);

        //
        // MOVE TIME AFTER CTD AND EXPECT BOTH EVENTS : NextEvent.CHANGE NextEvent.INVOICE
        //
        busHandler.pushExpectedEvent(NextEvent.CHANGE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        busHandler.pushExpectedEvent(NextEvent.PAYMENT);
        //clock.addDeltaFromReality(ctd.getMillis() - clock.getUTCNow().getMillis());
        clock.addDeltaFromReality(AT_LEAST_ONE_MONTH_MS + 1000);

        //waitForDebug();

        assertTrue(busHandler.isCompleted(DELAY));

        startDate = chargeThroughDate;
        endDate = chargeThroughDate.plusMonths(1);
        price = subscription.getCurrentPhase().getRecurringPrice().getPrice(Currency.USD);
        invoiceItemCount += 1;
        verifyTestResult(accountId, subscription.getId(), startDate, endDate, price, endDate, invoiceItemCount);

        //
        // MOVE TIME AFTER NEXT BILL CYCLE DAY AND EXPECT EVENT : NextEvent.INVOICE
        //
        int maxCycles = 3;
        do {
            busHandler.pushExpectedEvent(NextEvent.INVOICE);
            busHandler.pushExpectedEvent(NextEvent.PAYMENT);
            clock.addDeltaFromReality(AT_LEAST_ONE_MONTH_MS + 1000);
            assertTrue(busHandler.isCompleted(DELAY));

            startDate = endDate;
            endDate = startDate.plusMonths(1);
            if (endDate.dayOfMonth().get() != billingDay) {
                // adjust for end of month issues
                final int maximumDay = endDate.dayOfMonth().getMaximumValue();
                final int newDay = (maximumDay < billingDay) ? maximumDay : billingDay;
                endDate = endDate.toMutableDateTime().dayOfMonth().set(newDay).toDateTime();
            }

            invoiceItemCount += 1;
            verifyTestResult(accountId, subscription.getId(), startDate, endDate, price, endDate, invoiceItemCount);
        } while (maxCycles-- > 0);

        //
        // FINALLY CANCEL SUBSCRIPTION EOT
        //
        subscription = subscriptionDataFromSubscription(entitlementUserApi.getSubscriptionFromId(subscription.getId()));
        subscription.cancel(clock.getUTCNow(), false, context);

        // MOVE AFTER CANCEL DATE AND EXPECT EVENT : NextEvent.CANCEL
        busHandler.pushExpectedEvent(NextEvent.CANCEL);
        final Interval it = new Interval(clock.getUTCNow(), endDate);
        clock.addDeltaFromReality(it.toDurationMillis());
        assertTrue(busHandler.isCompleted(DELAY));

        //
        // CHECK AGAIN THERE IS NO MORE INVOICES GENERATED
        //
        busHandler.reset();
        clock.addDeltaFromReality(AT_LEAST_ONE_MONTH_MS + 1000);
        assertTrue(busHandler.isCompleted(DELAY));

        subscription = subscriptionDataFromSubscription(entitlementUserApi.getSubscriptionFromId(subscription.getId()));
        final DateTime lastCtd = subscription.getChargedThroughDate();
        assertNotNull(lastCtd);
        log.info("Checking CTD: " + lastCtd.toString() + "; clock is " + clock.getUTCNow().toString());
        assertTrue(lastCtd.isBefore(clock.getUTCNow()));

        // The invoice system is still working to verify there is nothing to do
        Thread.sleep(DELAY);

        assertListenerStatus();

        log.info("TEST PASSED !");
    }


    @Test(groups = "slow")
    public void testForMultipleRecurringPhases() throws Exception {

        log.info("Starting testForMultipleRecurringPhases");

        final DateTime initialCreationDate = new DateTime(2012, 2, 1, 0, 3, 42, 0);
        clock.setDeltaFromReality(initialCreationDate.getMillis() - clock.getUTCNow().getMillis());

        final Account account = createAccountWithPaymentMethod(getAccountData(2));
        final UUID accountId = account.getId();

        final String productName = "Blowdart";
        final String planSetName = "DEFAULT";

        busHandler.pushExpectedEvent(NextEvent.CREATE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        final SubscriptionBundle bundle = entitlementUserApi.createBundleForAccount(accountId, "testKey", context);
        subscriptionDataFromSubscription(entitlementUserApi.createSubscription(bundle.getId(),
                                                                               new PlanPhaseSpecifier(productName, ProductCategory.BASE,
                                                                                                      BillingPeriod.MONTHLY, planSetName, PhaseType.TRIAL), null, context));

        assertTrue(busHandler.isCompleted(DELAY));
        List<Invoice> invoices = invoiceUserApi.getInvoicesByAccount(accountId);
        assertNotNull(invoices);
        assertTrue(invoices.size() == 1);

        busHandler.pushExpectedEvent(NextEvent.PHASE);
        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        busHandler.pushExpectedEvent(NextEvent.PAYMENT);
        clock.addDeltaFromReality(AT_LEAST_ONE_MONTH_MS);
        assertTrue(busHandler.isCompleted(DELAY));
        invoices = invoiceUserApi.getInvoicesByAccount(accountId);
        assertNotNull(invoices);
        assertEquals(invoices.size(), 2);

        for (int i = 0; i < 5; i++) {
            log.info("============== loop number " + i + "=======================");
            busHandler.pushExpectedEvent(NextEvent.INVOICE);
            busHandler.pushExpectedEvent(NextEvent.PAYMENT);
            clock.addDeltaFromReality(AT_LEAST_ONE_MONTH_MS);
            assertTrue(busHandler.isCompleted(DELAY));
        }

        busHandler.pushExpectedEvent(NextEvent.INVOICE);
        busHandler.pushExpectedEvent(NextEvent.PAYMENT);
        busHandler.pushExpectedEvent(NextEvent.PHASE);
        clock.addDeltaFromReality(AT_LEAST_ONE_MONTH_MS);
        assertTrue(busHandler.isCompleted(DELAY));

        invoices = invoiceUserApi.getInvoicesByAccount(accountId);
        assertNotNull(invoices);
        assertEquals(invoices.size(), 8);

        for (int i = 0; i <= 5; i++) {
            log.info("============== second loop number " + i + "=======================");
            busHandler.pushExpectedEvent(NextEvent.INVOICE);
            busHandler.pushExpectedEvent(NextEvent.PAYMENT);
            clock.addDeltaFromReality(AT_LEAST_ONE_MONTH_MS);
            assertTrue(busHandler.isCompleted(DELAY));
        }

        invoices = invoiceUserApi.getInvoicesByAccount(accountId);
        assertNotNull(invoices);
        assertEquals(invoices.size(), 14);

        assertListenerStatus();
    }
}
