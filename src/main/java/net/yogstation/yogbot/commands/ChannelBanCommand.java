package net.yogstation.yogbot.commands;

import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.PartialMember;
import net.yogstation.yogbot.Yogbot;
import reactor.core.publisher.Mono;

public abstract class ChannelBanCommand extends PermissionsCommand {
	protected abstract Snowflake getBanRole();

	@Override
	protected Mono<?> doCommand(MessageCreateEvent event) {
		if(event.getMessage().getMemberMentions().size() != 1)
			return reply(event, "Usage is `%s%s [@UserName]`", Yogbot.config.discordConfig.commandPrefix, getName());
		PartialMember partialMember = event.getMessage().getMemberMentions().get(0);
		if(partialMember.getRoleIds().contains(getBanRole()))
			return partialMember.removeRole(getBanRole(), String.format("Ban lifted by %s", event.getMessage().getAuthor().isPresent() ? event.getMessage().getAuthor().get().getUsername() : "unknown"))
				.and(reply(event, "Ban lifted successfully"));
		return partialMember.addRole(getBanRole(), String.format("Ban applied by %s", event.getMessage().getAuthor().isPresent() ? event.getMessage().getAuthor().get().getUsername() : "unknown"))
			.and(reply(event, "Ban applied successfully"));
	}
}
