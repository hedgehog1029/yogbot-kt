package net.yogstation.yogbot.listeners.commands

import discord4j.core.event.domain.message.MessageCreateEvent
import net.yogstation.yogbot.DatabaseManager
import net.yogstation.yogbot.config.DiscordConfig
import net.yogstation.yogbot.permissions.PermissionsManager
import net.yogstation.yogbot.util.DiscordUtil
import net.yogstation.yogbot.util.StringUtils
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.sql.SQLException
import java.util.*

/**
 * Gets the activity of all the admins
 */
@Component
class ActivityCommand(
	discordConfig: DiscordConfig,
	permissions: PermissionsManager,
	private val database: DatabaseManager,
) : PermissionsCommand(discordConfig, permissions) {

	// Chonky query, grabs the needed data
	private val activityQueryFormat: String = """
			/*
MIT License
Copyright (c) 2021 alexkar598
Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:
The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.
THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/
SELECT adminlist.ckey as 'Ckey',
	COALESCE(round((SELECT SUM(rolelog.delta)
	FROM %s as rolelog
		WHERE rolelog.ckey = adminlist.ckey
			AND rolelog.job = 'Admin'
			AND rolelog.datetime > (Now() - INTERVAL 2 week)) / 60, 1), 0) as Activity,
	adminlist.rank as AdminRank
FROM %s as adminlist
JOIN %s as ranklist ON adminlist.rank = ranklist.`rank`;
	"""

	// The ranks to be listed as excempt
	private val exemptRanks = listOf(
		"Host", "Council Member", "RetCoder", "Tribunal",
		"Retired Admin", "Senior Coder", "Head Developer",
		"Maintainer", "Admin Observer", "#Forum Mod", "Bot",
		"Community Manager", "Retmin-Maintainerino"
	)

	// These ranks do not show up as all
	private val ignoreRanks = listOf("Maintainer", "Bot")

	override val description = "Activity fun."
	override val name = "activity"
	override val requiredPermissions = "note"

	public override fun doCommand(event: MessageCreateEvent): Mono<*> {
		return try {
			val conn = database.byondDbConnection
			val activityStatement = conn.createStatement()
			val activityResults = activityStatement.executeQuery(
				String.format(
					activityQueryFormat, database.prefix("role_time_log"), database.prefix("admin"),
					database.prefix("admin_ranks")
				)
			)

			// Iterates through the result of the query, saving ckey rank and activity, as well as the longest length
			// for name and rank, for formatting later
			val activityData: MutableList<Activity> = ArrayList()
			var adminLen = 8
			var rankLen = 4
			while (activityResults.next()) {
				if (ignoreRanks.contains(activityResults.getString("AdminRank"))) continue
				val activityDatum = Activity(
					activityResults.getString("Ckey"),
					activityResults.getString("AdminRank"),
					activityResults.getFloat("Activity")
				)
				activityData.add(activityDatum)
				if (activityDatum.ckey.length > adminLen) adminLen = activityDatum.ckey.length
				if (activityDatum.rank.length > rankLen) rankLen = activityDatum.rank.length
			}
			activityResults.close()
			activityStatement.close()
			activityData.sort()

			// Generate a set of all ckeys on LOA
			val loaAdmins: MutableSet<String> = HashSet()
			val loaStatement = conn.createStatement()
			val loaResults = loaStatement.executeQuery(
				"SELECT ckey from ${database.prefix("loa")} WHERE Now() < expiry_time && revoked IS NULL;"
			)
			while (loaResults.next()) loaAdmins.add(StringUtils.ckeyIze(loaResults.getString("ckey")))
			loaResults.close()
			loaStatement.close()
			conn.close()

			return printActivity(adminLen, rankLen, activityData, loaAdmins, event)
		} catch (e: SQLException) {
			logger.error("Error getting activity", e)
			DiscordUtil.reply(event, "Unable to reach the database, try again later")
		}
	}

	private fun printActivity(
		adminLen: Int,
		rankLen: Int,
		activityData: MutableList<Activity>,
		loaAdmins: MutableSet<String>,
		event: MessageCreateEvent
	): Mono<*> {
		// Print out the activity table
		val actions: MutableList<Mono<*>> = ArrayList()
		val output = StringBuilder("```diff\n")
		val title = StringBuilder("  ")
		title.append(StringUtils.center("Username", adminLen))
		title.append(" ")
		title.append(StringUtils.center("Rank", rankLen))
		title.append(" Activity")
		output.append(title)
		output.append('\n')
		output.append(StringUtils.padStart("", title.length, '='))
		output.append('\n')
		for (activity in activityData) {
			val line = StringBuilder()
			val loa = loaAdmins.contains(activity.ckey)
			val exempt = exemptRanks.contains(activity.rank)
			if (activity.activity >= 12) line.append('+') else if (loa || exempt) line.append(' ') else line.append(
				'-'
			)
			line.append(' ')
			line.append(StringUtils.padStart(activity.ckey, adminLen))
			line.append(' ')
			line.append(StringUtils.padStart(activity.rank, rankLen))
			line.append(' ')
			line.append(StringUtils.padStart(String.format(Locale.getDefault(), "%.1f", activity.activity), 8))
			line.append(' ')
			if (loa) line.append("(LOA)") else if (exempt) line.append("(Exempt)")
			line.append('\n')
			// If this line would make the message too big, send what we have and start a new one
			if (output.length + line.length > 1990) {
				output.append("```")
				actions.add(DiscordUtil.send(event, output.toString()))
				output.setLength(0) // Empty the string builder
				output.append("```diff\n")
			}
			output.append(line)
		}
		output.append("```")
		actions.add(DiscordUtil.send(event, output.toString()))
		return Mono.`when`(actions)
	}

	class Activity(val ckey: String, val rank: String, val activity: Float) : Comparable<Activity> {
		override fun compareTo(other: Activity): Int {
			return other.activity.compareTo(activity)
		}
	}
}
