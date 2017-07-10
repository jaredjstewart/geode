package org.apache.geode.management.internal.cli.result;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.apache.geode.management.cli.Result;
import org.apache.geode.management.internal.cli.json.GfJsonArray;
import org.apache.geode.management.internal.cli.json.GfJsonObject;
import org.apache.geode.test.junit.categories.UnitTest;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(UnitTest.class)
public class ResultBuilderTest {

  @Test
  public void messageExistsForString() throws Exception {
    CommandResult result = (CommandResult) ResultBuilder.createInfoResult("test message");
    assertThat(result.getContent().get("message")).isInstanceOf(JSONArray.class);
    assertThat(result.getContent().get("message").toString()).isEqualTo("[\"test message\"]");
  }

  @Test
  public void messageExistsForEmpty() throws Exception {
    CommandResult result = (CommandResult) ResultBuilder.createInfoResult("");
    assertThat(result.getContent().get("message")).isInstanceOf(JSONArray.class);
    assertThat(result.getContent().get("message").toString()).isEqualTo("[\"\"]");

  }

  @Test
  public void messageExistsForNull() throws Exception {
    CommandResult result = (CommandResult) ResultBuilder.createInfoResult(null);
    assertThat(result.getContent().get("message")).isInstanceOf(JSONArray.class);
    assertThat(result.getContent().get("message").toString()).isEqualTo("[null]");

  }

  @Test
  public void infoResultDataStructure() throws Exception {
    InfoResultData result = ResultBuilder.createInfoResultData();
    result.addLine("line 1");
    result.addLine("line 2");
    result.setFooter("Feet!");
    result.setHeader("Header");
    CommandResult cmdResult = (CommandResult) ResultBuilder.buildResult(result);

    assertThat(cmdResult.getGfJsonObject().has("header")).isTrue();
    assertThat(cmdResult.getGfJsonObject().has("content")).isTrue();
    assertThat(cmdResult.getGfJsonObject().has("footer")).isTrue();

    assertThat(cmdResult.getContent().has("message")).isTrue();

    assertThat(cmdResult.getStatus()).isEqualTo(Result.Status.OK);
  }

  @Test
  public void errorResultDataStructure() throws Exception {
    ErrorResultData result = ResultBuilder.createErrorResultData();
    result.addLine("line 1");
    result.addLine("line 2");
    result.setFooter("Feet!");
    result.setHeader("Header");
    CommandResult cmdResult = (CommandResult) ResultBuilder.buildResult(result);

    assertThat(cmdResult.getGfJsonObject().has("header")).isTrue();
    assertThat(cmdResult.getGfJsonObject().has("content")).isTrue();
    assertThat(cmdResult.getGfJsonObject().has("footer")).isTrue();

    assertThat(cmdResult.getContent().has("message")).isTrue();

    assertThat(cmdResult.getStatus()).isEqualTo(Result.Status.ERROR);
  }

  @Test
  public void tabularResultDataStructure() throws Exception {
    TabularResultData result = ResultBuilder.createTabularResultData();
    result.accumulate("column1", "value11");
    result.accumulate("column1", "value12");
    result.accumulate("column2", "value21");
    result.accumulate("column2", "value22");

    result.setFooter("Feet!");
    result.setHeader("Header");
    CommandResult cmdResult = (CommandResult) ResultBuilder.buildResult(result);

    assertThat(cmdResult.getGfJsonObject().has("header")).isTrue();
    assertThat(cmdResult.getGfJsonObject().has("content")).isTrue();
    assertThat(cmdResult.getGfJsonObject().has("footer")).isTrue();

    assertThat(cmdResult.getContent().has("column1")).isTrue();
    assertThat(cmdResult.getContent().has("column2")).isTrue();

    assertThat(cmdResult.getContent().getJSONArray("column1").toString()).contains("value11");
    assertThat(cmdResult.getContent().getJSONArray("column1").toString()).contains("value12");
    assertThat(cmdResult.getContent().getJSONArray("column2").toString()).contains("value21");
    assertThat(cmdResult.getContent().getJSONArray("column2").toString()).contains("value22");
  }

  @Test
  public void compositeResultDataStructure() throws Exception {
    CompositeResultData result = ResultBuilder.createCompositeResultData();

    result.setFooter("Feet!");
    result.setHeader("Header");

    assertThat(result.getGfJsonObject().has("header")).isTrue();
    assertThat(result.getGfJsonObject().has("content")).isTrue();
    assertThat(result.getGfJsonObject().has("footer")).isTrue();

    // build up an example
    result.addSection().addData("section 0 key", "section 0 value");
    result.addSection().addTable().accumulate("table 1 column", "table 1 value");

    result.addSection();

  }
}
