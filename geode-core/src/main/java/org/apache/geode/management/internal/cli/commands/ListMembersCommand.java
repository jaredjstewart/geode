package org.apache.geode.management.internal.cli.commands;

import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import org.omg.CORBA.INTERNAL;
import org.springframework.shell.core.annotation.CliCommand;
import org.springframework.shell.core.annotation.CliOption;

import org.apache.geode.distributed.DistributedMember;
import org.apache.geode.internal.cache.InternalCache;
import org.apache.geode.management.cli.CliMetaData;
import org.apache.geode.management.cli.ConverterHint;
import org.apache.geode.management.cli.Result;
import org.apache.geode.management.internal.cli.CliUtil;
import org.apache.geode.management.internal.cli.LogWrapper;
import org.apache.geode.management.internal.cli.i18n.CliStrings;
import org.apache.geode.management.internal.cli.result.ResultBuilder;
import org.apache.geode.management.internal.cli.result.TabularResultData;
import org.apache.geode.management.internal.cli.shell.OperationInvoker;
import org.apache.geode.management.internal.security.ResourceOperation;
import org.apache.geode.security.ResourcePermission;

public class ListMembersCommand {

  private final InternalCache cache;
  private final String group;

  public ListMembersCommand(String group, InternalCache cache) {
    this.group = group;
    this.cache = cache;
  }

  public Result run() {
    Result result;

    // TODO: Add the code for identifying the system services
    try {
      Set<DistributedMember> memberSet = new TreeSet<>();

      // default get all the members in the DS
      if (group.isEmpty()) {
        memberSet.addAll(CliUtil.getAllMembers(cache));
      } else {
        memberSet.addAll(cache.getDistributedSystem().getGroupMembers(group));
      }

      if (memberSet.isEmpty()) {
        result = ResultBuilder.createInfoResult(CliStrings.LIST_MEMBER__MSG__NO_MEMBER_FOUND);
      } else {
        TabularResultData resultData = ResultBuilder.createTabularResultData();
        for (DistributedMember member : memberSet) {
          resultData.accumulate("Name", member.getName());
          resultData.accumulate("Id", member.getId());
        }

        result = ResultBuilder.buildResult(resultData);
      }
    } catch (Exception e) {

      result = ResultBuilder
          .createGemFireErrorResult("Could not fetch the list of members. " + e.getMessage());
      LogWrapper.getInstance().warning(e.getMessage(), e);
    }
    return result;
  }
}
