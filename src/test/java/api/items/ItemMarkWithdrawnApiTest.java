package api.items;

import static api.support.InstanceSamples.smallAngryPlanet;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static support.matchers.ResponseMatchers.hasValidationError;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.inventory.support.http.client.IndividualResource;
import org.folio.inventory.support.http.client.Response;
import org.folio.inventory.support.http.client.ResponseHandler;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;
import org.junit.runner.RunWith;

import api.support.ApiTests;
import api.support.builders.HoldingRequestBuilder;
import api.support.builders.ItemRequestBuilder;
import api.support.http.BusinessLogicInterfaceUrls;
import io.vertx.core.json.JsonObject;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class ItemMarkWithdrawnApiTest extends ApiTests {

  @Parameters({
    "Available",
    "In transit",
    "Awaiting pickup",
    "Awaiting delivery",
    "Missing",
    "Paged"
  })
  @Test
  public void canMakeItemWithdrawn(String initialStatus) throws Exception {
    IndividualResource instance = instancesClient.create(smallAngryPlanet(UUID.randomUUID()));
    UUID holdingId = holdingsStorageClient.create(
      new HoldingRequestBuilder()
        .forInstance(instance.getId()))
      .getId();

    IndividualResource createdItem = itemsClient.create(new ItemRequestBuilder()
      .forHolding(holdingId)
      .withStatus(initialStatus)
      .canCirculate());

    final JsonObject withdrawnItem = markItemWithdrawn(createdItem.getId()).getJson();
    assertThat(withdrawnItem.getJsonObject("status").getString("name"),
      is("Withdrawn"));

    assertThat(itemsClient.getById(createdItem.getId()).getJson()
      .getJsonObject("status").getString("name"), is("Withdrawn"));
  }

  @Parameters({
    "On order",
    "Checked out",
    "Withdrawn",
    "Claimed returned"
  })
  @Test
  public void cannotMakeItemWithdrawnIfStatusIsNotAllowed(String initialStatus) throws Exception {
    IndividualResource instance = instancesClient.create(smallAngryPlanet(UUID.randomUUID()));
    UUID holdingId = holdingsStorageClient.create(
      new HoldingRequestBuilder()
        .forInstance(instance.getId()))
      .getId();

    IndividualResource createdItem = itemsClient.create(new ItemRequestBuilder()
      .forHolding(holdingId)
      .withStatus(initialStatus)
      .canCirculate());

    final Response response = markItemWithdrawn(createdItem.getId());

    assertThat(response, hasValidationError("Item is not allowed to be marked as Withdrawn",
      "status.name", initialStatus));
  }

  @Test
  public void shouldReturnNotFoundWhenSpecifiedWrongItemId() throws Exception {
    assertThat(markItemWithdrawn(UUID.randomUUID()).getStatusCode(),
      is(404));
  }

  @Test
  public void canChangeAwaitingPickupRequestToOpen() throws Exception {
    IndividualResource instance = instancesClient.create(smallAngryPlanet(UUID.randomUUID()));
    UUID holdingId = holdingsStorageClient.create(
      new HoldingRequestBuilder()
        .forInstance(instance.getId()))
      .getId();

    IndividualResource createdItem = itemsClient.create(new ItemRequestBuilder()
      .forHolding(holdingId)
      .withStatus("Awaiting pickup")
      .canCirculate());

    final IndividualResource request = createRequest(createdItem.getId(),
      "Open - Awaiting pickup", DateTime.now(DateTimeZone.UTC).plusHours(1));

    final JsonObject withdrawnItem = markItemWithdrawn(createdItem.getId()).getJson();
    assertThat(withdrawnItem.getJsonObject("status").getString("name"),
      is("Withdrawn"));

    assertThat(itemsClient.getById(createdItem.getId()).getJson()
      .getJsonObject("status").getString("name"), is("Withdrawn"));

    assertThat(requestStorageClient.getById(request.getId()).getJson()
      .getString("status"), is("Open - Not yet filled"));
  }

  @Test
  public void shouldNotChangeExpiredAwaitingPickupRequestToOpen() throws Exception {
    IndividualResource instance = instancesClient.create(smallAngryPlanet(UUID.randomUUID()));
    UUID holdingId = holdingsStorageClient.create(
      new HoldingRequestBuilder()
        .forInstance(instance.getId()))
      .getId();

    IndividualResource createdItem = itemsClient.create(new ItemRequestBuilder()
      .forHolding(holdingId)
      .withStatus("Awaiting pickup")
      .canCirculate());

    final IndividualResource request = createRequest(createdItem.getId(),
      "Open - Awaiting pickup", DateTime.now(DateTimeZone.UTC).minusHours(1));

    final JsonObject withdrawnItem = markItemWithdrawn(createdItem.getId()).getJson();
    assertThat(withdrawnItem.getJsonObject("status").getString("name"),
      is("Withdrawn"));

    assertThat(itemsClient.getById(createdItem.getId()).getJson()
      .getJsonObject("status").getString("name"), is("Withdrawn"));

    assertThat(requestStorageClient.getById(request.getId()).getJson()
      .getString("status"), is("Open - Awaiting pickup"));
  }

  @Test
  public void shouldNotChangeInTransitRequestToOpen() throws Exception {
    IndividualResource instance = instancesClient.create(smallAngryPlanet(UUID.randomUUID()));
    UUID holdingId = holdingsStorageClient.create(
      new HoldingRequestBuilder()
        .forInstance(instance.getId()))
      .getId();

    IndividualResource createdItem = itemsClient.create(new ItemRequestBuilder()
      .forHolding(holdingId)
      .withStatus("Awaiting pickup")
      .canCirculate());

    final IndividualResource request = createRequest(createdItem.getId(),
      "Open - In transit", DateTime.now(DateTimeZone.UTC).plusHours(1));

    final JsonObject withdrawnItem = markItemWithdrawn(createdItem.getId()).getJson();
    assertThat(withdrawnItem.getJsonObject("status").getString("name"),
      is("Withdrawn"));

    assertThat(itemsClient.getById(createdItem.getId()).getJson()
      .getJsonObject("status").getString("name"), is("Withdrawn"));

    assertThat(requestStorageClient.getById(request.getId()).getJson()
      .getString("status"), is("Open - In transit"));
  }

  private Response markItemWithdrawn(UUID itemId) throws InterruptedException,
    ExecutionException, TimeoutException {

    final CompletableFuture<Response> future = new CompletableFuture<>();

    okapiClient.post(BusinessLogicInterfaceUrls.markWithdrawn(itemId.toString()),
      null, ResponseHandler.any(future));

    return future.get(5, TimeUnit.SECONDS);
  }

  private IndividualResource createRequest(UUID itemId,
    String status, DateTime expireDateTime)
    throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {

    final JsonObject request = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("status", status)
      .put("itemId", itemId.toString())
      .put("holdShelfExpirationDate", expireDateTime.toString())
      .put("requesterId", UUID.randomUUID().toString())
      .put("requestType", "Hold")
      .put("requestDate", DateTime.now(DateTimeZone.UTC).toString())
      .put("fulfilmentPreference", "Hold shelf");

    return requestStorageClient.create(request);
  }
}
