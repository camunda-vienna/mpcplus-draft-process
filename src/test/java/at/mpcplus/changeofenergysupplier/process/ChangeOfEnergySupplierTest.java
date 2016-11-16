package at.mpcplus.changeofenergysupplier.process;

import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.withVariables;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.extension.process_test_coverage.junit.rules.TestCoverageProcessEngineRuleBuilder;
import org.camunda.bpm.scenario.ProcessScenario;
import org.camunda.bpm.scenario.Scenario;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@Deployment(resources = {"ChangeOfEnergySupplier.bpmn", "DetermineCommunicationChannels.dmn"})
@RunWith(MockitoJUnitRunner.class)
public class ChangeOfEnergySupplierTest {

  @Rule
  @ClassRule
  public static ProcessEngineRule rule = TestCoverageProcessEngineRuleBuilder.create().build();

  @Mock
  public ProcessScenario changeOfEnergySupplier;

  @Test
  public void testHappyPath() throws Exception {

    Scenario.run(changeOfEnergySupplier).startByKey("ChangeOfEnergySupplier").execute();

    verify(changeOfEnergySupplier, times(1)).hasFinished("ProvisionInvoiceable");
    verify(changeOfEnergySupplier, never()).hasFinished("ApplicationCanceled");
    verify(changeOfEnergySupplier, never()).hasFinished("ApplicationTimedOut");
    verify(changeOfEnergySupplier, never()).hasFinished("ApplicationSpeedUpFinished");
    
  }

  @Test
  public void testHappyPath_viaMail() throws Exception {

    Map<String, Object> variables = withVariables("viaMail", true, "viaPostalService", false);

    when(changeOfEnergySupplier.waitsAtReceiveTask("GatherNeededApplicationData")).thenReturn((task) ->
      rule.getRuntimeService()
        .createMessageCorrelation(task.getEventName())
        .setVariables(variables)
        .correlateExclusively()
    );

    Scenario.run(changeOfEnergySupplier).startByKey("ChangeOfEnergySupplier").execute();

    verify(changeOfEnergySupplier, times(1)).hasFinished("ProvisionInvoiceable");
    verify(changeOfEnergySupplier, times(1)).hasFinished("DeliverApplicationViaMail");
    verify(changeOfEnergySupplier, never()).hasFinished("DeliverApplicationViaPostalService");

  }

  @Test
  public void testHappyPath_viaPostalService() throws Exception {

    Map<String, Object> variables = withVariables("viaMail", false, "viaPostalService", true);

    when(changeOfEnergySupplier.waitsAtReceiveTask("GatherNeededApplicationData")).thenReturn((task) ->
      rule.getRuntimeService()
        .createMessageCorrelation(task.getEventName())
        .setVariables(variables)
        .correlateExclusively()
    );

    Scenario.run(changeOfEnergySupplier).startByKey("ChangeOfEnergySupplier").execute();

    verify(changeOfEnergySupplier, times(1)).hasFinished("DeliverApplicationViaPostalService");
    verify(changeOfEnergySupplier, never()).hasFinished("DeliverApplicationViaMail");
    verify(changeOfEnergySupplier, times(1)).hasFinished("ProvisionInvoiceable");

  }

  @Test
  public void testHappyPath_viaMailAndPostalService() throws Exception {

    Map<String, Object> variables = withVariables("viaMail", true, "viaPostalService", true);

    when(changeOfEnergySupplier.waitsAtReceiveTask("GatherNeededApplicationData")).thenReturn((task) ->
      rule.getRuntimeService()
        .createMessageCorrelation(task.getEventName())
        .setVariables(variables)
        .correlateExclusively()
    );

    Scenario.run(changeOfEnergySupplier).startByKey("ChangeOfEnergySupplier").execute();

    verify(changeOfEnergySupplier, times(1)).hasFinished("DeliverApplicationViaPostalService");
    verify(changeOfEnergySupplier, times(1)).hasFinished("DeliverApplicationViaMail");
    verify(changeOfEnergySupplier, times(1)).hasFinished("ProvisionInvoiceable");

  }

  @Test
  public void testApplicationCanceled() throws Exception {

    when(changeOfEnergySupplier.waitsAtReceiveTask("SignApplication")).thenReturn((task) -> {
      rule.getRuntimeService().correlateMessage("msgApplicationIsCanceled");
    });

    Scenario.run(changeOfEnergySupplier).startByKey("ChangeOfEnergySupplier").execute();

    verify(changeOfEnergySupplier, never()).hasFinished("ProvisionInvoiceable");
    verify(changeOfEnergySupplier, times(1)).hasFinished("ApplicationCanceled");

  }

  @Test
  public void testApplicationTimedOut() throws Exception {

    when(changeOfEnergySupplier.waitsAtReceiveTask("SignApplication")).thenReturn((task) ->
      task.defer("P200D", () -> task.receive())
    );

    Scenario.run(changeOfEnergySupplier).startByKey("ChangeOfEnergySupplier").execute();

    verify(changeOfEnergySupplier, never()).hasFinished("ProvisionInvoiceable");
    verify(changeOfEnergySupplier, times(3)).hasFinished("ProcedureSpedUp");
    verify(changeOfEnergySupplier, times(1)).hasFinished("ApplicationTimedOut");
  }

  @Test
  public void testSignApplication_afterTenDays() throws Exception {

    when(changeOfEnergySupplier.waitsAtReceiveTask("SignApplication")).thenReturn((task) ->
      task.defer("P10D", () -> task.receive())
    );

    Scenario.run(changeOfEnergySupplier).startByKey("ChangeOfEnergySupplier").execute();

    verify(changeOfEnergySupplier, times(1)).hasFinished("ProvisionInvoiceable");
    verify(changeOfEnergySupplier, times(1)).hasFinished("ProcedureSpedUp");

  }

  @Test
  public void testSignApplication_afterTwentyDays() throws Exception {

    when(changeOfEnergySupplier.waitsAtReceiveTask("SignApplication")).thenReturn((task) ->
        task.defer("P20D", () -> task.receive())
    );

    Scenario.run(changeOfEnergySupplier).startByKey("ChangeOfEnergySupplier").execute();

    verify(changeOfEnergySupplier, times(1)).hasFinished("ProvisionInvoiceable");
    verify(changeOfEnergySupplier, times(2)).hasFinished("ProcedureSpedUp");
  }

  @Test
  public void testGatherNeededApplicationData_twoAttempts() throws Exception {

    when(changeOfEnergySupplier.waitsAtBusinessRuleTask("DetermineWhetherReadyToBeSigned"))
      .thenReturn((task) -> {
        task.complete(withVariables("applicationReadyToBeSigned", false));
      }).thenReturn((task) -> {
        task.complete(withVariables("applicationReadyToBeSigned", true));
      });

    Scenario.run(changeOfEnergySupplier).startByKey("ChangeOfEnergySupplier").execute();

    verify(changeOfEnergySupplier, times(2)).hasFinished("GatherNeededApplicationData");
    verify(changeOfEnergySupplier, times(2)).hasFinished("DetermineWhetherReadyToBeSigned");
    verify(changeOfEnergySupplier, times(1)).hasFinished("ProvisionInvoiceable");

  }

  @Test
  public void testGatherNeededApplicationData_afterTenDays() throws Exception {

    when(changeOfEnergySupplier.waitsAtReceiveTask("GatherNeededApplicationData")).thenReturn((task) -> {
      task.defer("P10D", () -> rule.getRuntimeService().createMessageCorrelation(task.getEventName()).setVariables(withVariables("viaMail", true, "viaPostalService", false)).correlateExclusively());
    });

    Scenario.run(changeOfEnergySupplier).startByKey("ChangeOfEnergySupplier").execute();

    verify(changeOfEnergySupplier, times(1)).hasFinished("ProvisionInvoiceable");
    verify(changeOfEnergySupplier, never()).hasFinished("ProcedureSpedUp");
    verify(changeOfEnergySupplier, times(1)).hasFinished("ProcedureNotSpedUp");

  }

  @Test
  public void testGatherMissingApplicationData() throws Exception {

    when(changeOfEnergySupplier.waitsAtBusinessRuleTask("DetermineWhetherReadyToBeSubmitted")).thenReturn((task) ->
        task.complete(withVariables("applicationReadyToBeSubmitted", false))
    ).thenReturn((task) ->
        task.complete(withVariables("applicationReadyToBeSubmitted", true))
    );

    Scenario.run(changeOfEnergySupplier).startByKey("ChangeOfEnergySupplier").execute();

    verify(changeOfEnergySupplier, times(2)).hasFinished("DetermineWhetherReadyToBeSubmitted");
    verify(changeOfEnergySupplier, times(1)).hasFinished("GatherMissingApplicationData");
    verify(changeOfEnergySupplier, times(1)).hasFinished("ProvisionInvoiceable");

  }

  @Test
  public void testGatherMissingApplicationData_afterTenDays() throws Exception {

    when(changeOfEnergySupplier.waitsAtBusinessRuleTask("DetermineWhetherReadyToBeSubmitted")).thenReturn((task) ->
        task.complete(withVariables("applicationReadyToBeSubmitted", false))
    ).thenReturn((task) ->
        task.complete(withVariables("applicationReadyToBeSubmitted", true))
    );

    when(changeOfEnergySupplier.waitsAtReceiveTask("GatherMissingApplicationData")).thenReturn((task) ->
        task.defer("P10D", () -> task.receive())
    );

    Scenario.run(changeOfEnergySupplier).startByKey("ChangeOfEnergySupplier").execute();

    verify(changeOfEnergySupplier, times(1)).hasFinished("ProvisionInvoiceable");
    verify(changeOfEnergySupplier, times(2)).hasFinished("DetermineWhetherReadyToBeSubmitted");
    verify(changeOfEnergySupplier, times(1)).hasFinished("GatherMissingApplicationData");
    verify(changeOfEnergySupplier, times(1)).hasFinished("ProcedureSpedUp");

  }

  @Test
  public void testGatherMissingApplicationData_afterTwentyDays() throws Exception {

    when(changeOfEnergySupplier.waitsAtBusinessRuleTask("DetermineWhetherReadyToBeSubmitted")).thenReturn((task) ->
        // At pass #1 NOT READY to be submitted
        task.complete(withVariables("applicationReadyToBeSubmitted", false))
    ).thenReturn((task) ->
        // At pass #2 READY to be submitted
        task.complete(withVariables("applicationReadyToBeSubmitted", true))
    );

    when(changeOfEnergySupplier.waitsAtReceiveTask("GatherMissingApplicationData")).thenReturn((task) ->
        // The missing application data comes after 20 days of waiting
        task.defer("P20D", () -> task.receive())
    );

    Scenario.run(changeOfEnergySupplier).startByKey("ChangeOfEnergySupplier").execute();

    // We have been waiting for missing application data and then received it
    verify(changeOfEnergySupplier, times(1)).hasFinished("GatherMissingApplicationData");

    // While waiting, we reminded the customer once by mail and once by phone
    verify(changeOfEnergySupplier, times(1)).hasFinished("SendReminderViaMail");
    verify(changeOfEnergySupplier, times(1)).hasFinished("ContactCustomerViaPhone");
    verify(changeOfEnergySupplier, times(2)).hasFinished("ProcedureSpedUp");

    // Finally we reach the happy end :-)
    verify(changeOfEnergySupplier, times(1)).hasFinished("ProvisionInvoiceable");

  }

  @Before
  public void init() {
    init(changeOfEnergySupplier);
  }

  public static void init(ProcessScenario changeOfEnergySupplier) {
    when(changeOfEnergySupplier.waitsAtReceiveTask("GatherNeededApplicationData")).thenReturn((task) -> {
      rule.getRuntimeService()
          .createMessageCorrelation(
              // Lesbarkeit
              task.getEventName()).setVariables(withVariables("viaMail", true, "viaPostalService", false))
          .correlateExclusively();
    });
    when(changeOfEnergySupplier.waitsAtBusinessRuleTask("DetermineWhetherReadyToBeSigned")).thenReturn((task) -> {
      task.complete(withVariables("applicationReadyToBeSigned", true));
    });
    when(changeOfEnergySupplier.waitsAtReceiveTask("SignApplication")).thenReturn((task) -> {
      task.receive();
    });
    when(changeOfEnergySupplier.waitsAtSendTask("DeliverApplicationViaMail")).thenReturn((task) -> {
      task.complete();
    });
    when(changeOfEnergySupplier.waitsAtSendTask("DeliverApplicationViaMail")).thenReturn((task) -> {
      task.complete();
    });
    when(changeOfEnergySupplier.waitsAtUserTask("DeliverApplicationViaPostalService")).thenReturn((task) -> {
      task.complete();
    });
    when(changeOfEnergySupplier.waitsAtBusinessRuleTask("DetermineWhetherReadyToBeSubmitted")).thenReturn((task) -> {
      task.complete(withVariables("applicationReadyToBeSubmitted", true));
    });
    when(changeOfEnergySupplier.waitsAtReceiveTask("GatherMissingApplicationData")).thenReturn((task) -> {
      task.receive();
    });
    when(changeOfEnergySupplier.waitsAtSendTask("SubmitApplication")).thenReturn((task) -> {
      task.complete();
    });
    when(changeOfEnergySupplier.waitsAtSendTask("SendReminderViaMail")).thenReturn((task) -> {
      task.complete();
    });
    when(changeOfEnergySupplier.waitsAtUserTask("ContactCustomerViaPhone")).thenReturn((task) -> {
      task.complete();
    });
  }

}
