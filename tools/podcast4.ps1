chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$root = 'C:\Users\msn\Documents\SuperDL-Android\app\src\main\kotlin\com\superdl\launcher'
Write-Output "=== navigatePodcastList / openPodcast / enterPodcastEpisodeMenu hivasi helyei (MINDEN fajlban) ==="
Get-ChildItem -Path $root -Recurse -Filter *.kt |
  Select-String -Pattern 'navigatePodcastList|openPodcast\(|enterPodcastEpisodeMenu\(|navigatePodcastEpisodes\(' |
  ForEach-Object { Write-Output ("  " + $_.Filename + ":" + $_.LineNumber + "  " + $_.Line.Trim()) }
Write-Output ""
Write-Output "=== PodcastListBrowse / PodcastEpisodeBrowse elofordulasai (MINDEN fajlban) ==="
Get-ChildItem -Path $root -Recurse -Filter *.kt |
  Select-String -Pattern 'PodcastListBrowse|PodcastEpisodeBrowse|PodcastEpisodeMenu|PodcastCountryBrowse' |
  ForEach-Object { Write-Output ("  " + $_.Filename + ":" + $_.LineNumber + "  " + $_.Line.Trim()) }
