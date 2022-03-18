package net.yogstation.yogbot.interactions;

import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import discord4j.core.event.domain.interaction.UserInteractionEvent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.MessageComponent;
import discord4j.core.object.component.TextInput;
import discord4j.discordjson.json.ComponentData;
import net.yogstation.yogbot.Yogbot;
import reactor.core.publisher.Mono;


public class SoftbanCommand implements IInteractionHandler<UserInteractionEvent>, IModalSubmitHandler {
	@Override
	public String getName() {
		return "Softban";
	}

	@Override
	public Mono<?> handle(UserInteractionEvent event) {
		return event.presentModal()
			.withCustomId(String.format("%s-%s", getIdPrefix(), event.getTargetId().asString()))
			.withTitle("Softban Menu")
			.withComponents(ActionRow.of(
				TextInput.small("duration", "Ban Duration (Minutes)").required(false)
			), ActionRow.of(
				TextInput.paragraph("reason", "Ban Reason")
			)
		);
	}


	@Override
	public String getIdPrefix() {
		return "softban";
	}

	@Override
	public Mono<?> handle(ModalSubmitInteractionEvent event) {
		Snowflake toBan = Snowflake.of(event.getCustomId().split("-")[1]);
		int duration = -1;
		String reason = "";

		for(MessageComponent component : event.getComponents()) {
			if(component.getType() == MessageComponent.Type.ACTION_ROW) {
				if(component.getData().components().isAbsent()) continue;
				for(ComponentData data : component.getData().components().get()) {
					if(data.customId().isAbsent()) continue;
					switch (data.customId().get()) {
						case "duration":
							if(data.value().isAbsent())
								duration = -1;
							else
								duration = Integer.parseInt(data.value().get());
							break;
						case "reason":
							if(data.value().isAbsent())
								return event.reply().withContent("Please specify a ban reason");
							reason = data.value().get();
					}
				}
			}
		}
		int finalDuration = duration;
		String finalReason = reason;
		return event.getInteraction().getGuild().flatMap(guild ->
			guild.getMemberById(toBan)).flatMap(member -> Yogbot.banManager.ban(member, finalReason, finalDuration, event.getInteraction().getUser().getUsername()).and(
				event.reply().withEphemeral(true).withContent("Ban issued successfully")));
	}
}
